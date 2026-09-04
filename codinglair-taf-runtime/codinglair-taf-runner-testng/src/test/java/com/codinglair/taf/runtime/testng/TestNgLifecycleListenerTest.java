package com.codinglair.taf.runtime.testng;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.testng.IConfigurationListener;
import org.testng.IRetryAnalyzer;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.TestNG;
import org.testng.annotations.DataProvider;

class TestNgLifecycleListenerTest {
  @Test
  void parallelDataProviderInvocationsReceiveIsolatedSessionsAndCleanup() {
    ParallelConsumer.sessionIds.clear();
    ParallelConsumer.cleanedSessionIds.clear();
    ParallelConsumer.cleanedScopes.clear();
    TestNG testng = testNg(ParallelConsumer.class);

    testng.run();

    assertThat(testng.hasFailure()).isFalse();
    assertThat(ParallelConsumer.sessionIds).hasSize(8);
    assertThat(ParallelConsumer.cleanedSessionIds)
        .containsExactlyInAnyOrderElementsOf(ParallelConsumer.sessionIds);
    assertThat(ParallelConsumer.cleanedScopes).isEmpty();
  }

  @Test
  void cleanupFailureFailsAnOtherwiseSuccessfulInvocation() {
    TestNG testng = testNg(CleanupFailureConsumer.class);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    testng.addListener(
        new IConfigurationListener() {
          @Override
          public void onConfigurationFailure(ITestResult result) {
            failure.set(result.getThrowable());
          }
        });

    testng.run();

    assertThat(testng.hasFailure()).isTrue();
    assertThat(CleanupFailureConsumer.bodyCompleted).isTrue();
    assertThat(failure.get())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("cleanup failed");
  }

  @Test
  void retryAttemptsHaveIsolatedSessionsAndRetainNativeResultHistory() {
    RetryConsumer.invocations.set(0);
    RetryConsumer.sessionIds.clear();
    RetryConsumer.cleanedSessionIds.clear();
    List<ITestResult> attempts = new CopyOnWriteArrayList<>();
    TestNG testng = testNg(RetryConsumer.class);
    testng.addListener(
        new ITestListener() {
          @Override
          public void onTestFailure(ITestResult result) {
            attempts.add(result);
          }

          @Override
          public void onTestSuccess(ITestResult result) {
            attempts.add(result);
          }

          @Override
          public void onTestSkipped(ITestResult result) {
            attempts.add(result);
          }
        });

    testng.run();

    assertThat(testng.hasFailure()).isFalse();
    assertThat(attempts).hasSize(2);
    assertThat(attempts)
        .extracting(ITestResult::getStatus)
        .containsExactlyInAnyOrder(ITestResult.SKIP, ITestResult.SUCCESS);
    ITestResult failedAttempt =
        attempts.stream()
            .filter(result -> result.getStatus() == ITestResult.SKIP)
            .findFirst()
            .orElseThrow();
    assertThat(failedAttempt.getThrowable())
        .isInstanceOf(AssertionError.class)
        .hasMessage("first attempt");
    assertThat(RetryConsumer.sessionIds).hasSize(2);
    assertThat(RetryConsumer.cleanedSessionIds)
        .containsExactlyInAnyOrderElementsOf(RetryConsumer.sessionIds);
  }

  @Test
  void retryRetainsAttemptHistoryWithoutPublishingDuplicateShallowReporterEvents() {
    RetryConsumer.invocations.set(0);
    CapturingReporter reporter = new CapturingReporter();
    List<TestNgExecutionResult> published = new CopyOnWriteArrayList<>();
    TestNG testng = testNgWithoutServiceDiscovery(RetryConsumer.class);
    testng.addListener(new TestNgLifecycleListener(List.of(reporter), List.of(published::add)));

    testng.run();

    assertThat(testng.hasFailure()).isFalse();
    assertThat(published)
        .singleElement()
        .satisfies(
            execution -> {
              assertThat(execution.attempts()).hasSize(2);
              assertThat(execution.attempts())
                  .extracting(TestNgAttemptResult::status)
                  .containsExactly(
                      TestNgAttemptResult.Status.SKIPPED, TestNgAttemptResult.Status.PASSED);
              assertThat(execution.attempts().getFirst().failure())
                  .isInstanceOf(AssertionError.class);
              assertThat(execution.attempts())
                  .allSatisfy(
                      attempt ->
                          assertThat(attempt.artifacts())
                              .singleElement()
                              .extracting(TestArtifact::name)
                              .isEqualTo("attempt-evidence"));
            });
    assertThat(reporter.begun).isEmpty();
    assertThat(reporter.steps).isEmpty();
    assertThat(reporter.artifacts).isEmpty();
    assertThat(reporter.ended).isEmpty();
    String structured = new TestNgStructuredResultWriter().writeJson(published.getFirst());
    assertThat(structured)
        .contains(
            "\"attemptNumber\":1",
            "\"attemptNumber\":2",
            "\"status\":\"SKIPPED\"",
            "\"status\":\"PASSED\"",
            "attempt-evidence");
  }

  @Test
  void skippedInvocationStillOwnsAndCleansExactlyOneSession() {
    SkippedConsumer.sessions.clear();
    SkippedConsumer.cleaned.clear();

    TestNG testng = testNg(SkippedConsumer.class);
    testng.run();

    assertThat(SkippedConsumer.sessions).hasSize(1);
    assertThat(SkippedConsumer.cleaned).containsExactlyElementsOf(SkippedConsumer.sessions);
  }

  @Test
  void assertionRemainsPrimaryWhenCleanupAlsoFails() {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    TestNG testng = testNg(AssertionAndCleanupFailureConsumer.class);
    testng.addListener(
        new ITestListener() {
          @Override
          public void onTestFailure(ITestResult result) {
            failure.set(result.getThrowable());
          }
        });

    testng.run();

    assertThat(testng.hasFailure()).isTrue();
    assertThat(failure.get()).isInstanceOf(AssertionError.class).hasMessage("primary assertion");
    assertThat(failure.get().getSuppressed())
        .singleElement()
        .satisfies(
            cleanup ->
                assertThat(cleanup)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("secondary cleanup"));
  }

  private static TestNG testNg(Class<?> testClass) {
    TestNG testng = new TestNG(false);
    testng.setUseDefaultListeners(false);
    testng.setTestClasses(new Class<?>[] {testClass});
    return testng;
  }

  private static TestNG testNgWithoutServiceDiscovery(Class<?> testClass) {
    TestNG testng = new TestNG(false);
    testng.setUseDefaultListeners(false);
    testng.setServiceLoaderClassLoader(new URLClassLoader(new URL[0], null));
    testng.setTestClasses(new Class<?>[] {testClass});
    return testng;
  }

  public static class ParallelConsumer extends TafBaseTest {
    static final Set<String> sessionIds = ConcurrentHashMap.newKeySet();
    static final Set<String> cleanedSessionIds = ConcurrentHashMap.newKeySet();
    static final Set<String> cleanedScopes = ConcurrentHashMap.newKeySet();

    @DataProvider(parallel = true)
    public Object[][] values() {
      Object[][] values = new Object[8][1];
      for (int i = 0; i < values.length; i++) values[i][0] = i;
      return values;
    }

    @org.testng.annotations.Test(dataProvider = "values")
    public void isolated(int invocation) {
      ITestResult result = org.testng.Reporter.getCurrentTestResult();
      TestSession session = SessionFactory.getCurrentSession(result);
      sessionIds.add(session.getSessionId());
      session.addCleanupListener(cleanedSessionIds::add);
      if (invocation == 0) {}
    }
  }

  public static class CleanupFailureConsumer extends TafBaseTest {
    static volatile boolean bodyCompleted;

    @org.testng.annotations.Test
    public void cleanupFailureIsVisible() {
      bodyCompleted = true;
      SessionFactory.getCurrentSession(org.testng.Reporter.getCurrentTestResult())
          .addCleanupListener(
              ignored -> {
                throw new IllegalStateException("cleanup failed");
              });
    }
  }

  public static class RetryConsumer extends TafBaseTest {
    static final AtomicInteger invocations = new AtomicInteger();
    static final Set<String> sessionIds = ConcurrentHashMap.newKeySet();
    static final Set<String> cleanedSessionIds = ConcurrentHashMap.newKeySet();

    @org.testng.annotations.Test(retryAnalyzer = RetryOnce.class)
    public void succeedsOnRetry() {
      TestSession session =
          SessionFactory.getCurrentSession(org.testng.Reporter.getCurrentTestResult());
      SessionFactory.addArtifact(
          org.testng.Reporter.getCurrentTestResult(),
          TestArtifact.of("attempt-evidence", "text", "safe", "text/plain"));
      sessionIds.add(session.getSessionId());
      session.addCleanupListener(cleanedSessionIds::add);
      if (invocations.getAndIncrement() == 0) {
        throw new AssertionError("first attempt");
      }
    }
  }

  public static class SkippedConsumer extends TafBaseTest {
    static final Set<String> sessions = ConcurrentHashMap.newKeySet();
    static final Set<String> cleaned = ConcurrentHashMap.newKeySet();

    @org.testng.annotations.Test
    public void skips() {
      TestSession session = testSession();
      sessions.add(session.getSessionId());
      session.addCleanupListener(cleaned::add);
      throw new SkipException("intentional skip");
    }
  }

  public static class AssertionAndCleanupFailureConsumer extends TafBaseTest {
    @org.testng.annotations.Test
    public void failsTwice() {
      testSession()
          .addCleanupListener(
              ignored -> {
                throw new IllegalStateException("secondary cleanup");
              });
      throw new AssertionError("primary assertion");
    }
  }

  private static final class CapturingReporter implements TestReporter {
    private final List<TafTest> begun = new CopyOnWriteArrayList<>();
    private final List<TafTest> ended = new CopyOnWriteArrayList<>();
    private final List<TestStep> steps = new CopyOnWriteArrayList<>();
    private final List<TestArtifact> artifacts = new CopyOnWriteArrayList<>();

    @Override
    public void beginTest(TafTest test) {
      begun.add(test);
    }

    @Override
    public void endTest(TafTest test) {
      ended.add(test);
    }

    @Override
    public void reportStep(TestStep step) {
      steps.add(step);
    }

    @Override
    public void reportArtifact(TestArtifact artifact) {
      artifacts.add(artifact);
    }

    @Override
    public String getName() {
      return "capture";
    }
  }

  public static class RetryOnce implements IRetryAnalyzer {
    private final AtomicInteger retries = new AtomicInteger();

    @Override
    public boolean retry(ITestResult result) {
      return retries.getAndIncrement() == 0;
    }
  }
}
