package com.codinglair.taf.runtime.testng;

import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import com.codinglair.taf.runtime.core.history.AttemptHistoryService;
import com.codinglair.taf.runtime.core.history.DisabledExecutionHistoryRepository;
import com.codinglair.taf.runtime.core.history.ExecutionAttemptSummary;
import com.codinglair.taf.runtime.core.history.HistoryConfiguration;
import com.codinglair.taf.runtime.core.failure.FailureClassificationService;
import com.codinglair.taf.runtime.core.failure.FailureSignatureService;
import com.codinglair.taf.runtime.core.reporting.ReporterDispatcher;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.Duration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.IConfigurationListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.ITestNGMethod;

/** Bridges TestNG lifecycles to isolated sessions and vendor-neutral execution reporting. */
public final class TestNgLifecycleListener
    implements ISuiteListener, ITestListener, IInvokedMethodListener, IConfigurationListener {

  private static final String STATE_KEY = TestNgLifecycleListener.class.getName() + ".state";
  private static final String PENDING_KEY = TestNgLifecycleListener.class.getName() + ".pending";
  private static final String RESULTS_KEY = TestNgLifecycleListener.class.getName() + ".results";
  private static final String CONFIGURATION_RECORDED_KEY = TestNgLifecycleListener.class.getName() + ".configuration-recorded";
  static final String ANALYSIS_KEY = TestNgLifecycleListener.class.getName() + ".failure-analysis";

  private final List<ReporterDispatcher> reporters;
  private final List<TestNgResultListener> resultListeners;
  private final AttemptHistoryService attemptHistory;

  public TestNgLifecycleListener() {
    this(load(TestReporter.class), load(TestNgResultListener.class), disabledHistory());
  }

  /**
   * Creates an adapter with explicit providers, primarily for deterministic embedding and tests.
   */
  public TestNgLifecycleListener(
      List<TestReporter> reporters, List<TestNgResultListener> resultListeners) {
    this(reporters, resultListeners, disabledHistory());
  }

  public TestNgLifecycleListener(List<TestReporter> reporters, List<TestNgResultListener> resultListeners,
      AttemptHistoryService attemptHistory) {
    RedactionPipeline redaction = new RedactionPipeline();
    this.reporters =
        reporters.stream().map(reporter -> new ReporterDispatcher(reporter, redaction)).toList();
    this.resultListeners = List.copyOf(resultListeners);
    this.attemptHistory = java.util.Objects.requireNonNull(attemptHistory);
  }

  @Override
  public void onStart(ISuite suite) {}

  @Override
  public void onFinish(ISuite suite) {}

  @Override
  public void onStart(ITestContext context) {
    context.setAttribute(STATE_KEY, new ConcurrentHashMap<String, ExecutionState>());
    context.setAttribute(RESULTS_KEY, new CopyOnWriteArrayList<TestNgExecutionResult>());
  }

  @Override
  public void onFinish(ITestContext context) {}

  @Override
  public void beforeInvocation(IInvokedMethod method, ITestResult result) {
    // Observation only. TafBaseTest or TafCucumberHooks owns the session.
  }

  @Override
  public void afterInvocation(IInvokedMethod method, ITestResult result) {
    if (!method.isTestMethod() || isCucumberRunnerInvocation(result)) {
      return;
    }
    List<com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact> artifacts =
        SessionFactory.snapshotArtifacts(result);
    result.setAttribute(PENDING_KEY, new PendingAttempt(Instant.now(), artifacts));
  }

  @Override
  public void onTestSuccess(ITestResult result) {
    record(result, TestNgAttemptResult.Status.PASSED);
  }

  @Override
  public void onTestFailure(ITestResult result) {
    record(result, TestNgAttemptResult.Status.FAILED);
  }

  @Override
  public void onTestSkipped(ITestResult result) {
    // TestNG retains the original assertion on retry-driven skips, while a test suppressed by a
    // failed configuration method carries that configuration throwable. wasRetried() is therefore
    // the necessary discriminator; throwable type alone cannot distinguish these paths.
    Throwable failure = result.getThrowable();
    record(result, !result.wasRetried() && failure != null
            && !(failure instanceof org.testng.SkipException)
        ? TestNgAttemptResult.Status.CONFIGURATION_FAILED
        : TestNgAttemptResult.Status.SKIPPED);
  }

  @Override
  public void onConfigurationFailure(ITestResult result) {
    if (result.getThrowable() != null && result.getAttribute(CONFIGURATION_RECORDED_KEY) == null) {
      result.setAttribute(CONFIGURATION_RECORDED_KEY, Boolean.TRUE);
      record(result, TestNgAttemptResult.Status.CONFIGURATION_FAILED);
    }
  }

  @Override
  public void onConfigurationFailure(ITestResult result, ITestNGMethod method) {
    onConfigurationFailure(result);
  }

  /** Returns immutable completed logical results retained for this TestNG test context. */
  public static List<TestNgExecutionResult> getResults(ITestContext context) {
    Object value = context.getAttribute(RESULTS_KEY);
    if (!(value instanceof List<?> results)) {
      return List.of();
    }
    return results.stream().map(TestNgExecutionResult.class::cast).toList();
  }

  private void record(ITestResult result, TestNgAttemptResult.Status status) {
    if (isCucumberRunnerInvocation(result)) {
      return;
    }
    PendingAttempt pending = (PendingAttempt) result.getAttribute(PENDING_KEY);
    result.removeAttribute(PENDING_KEY);
    if (pending == null) {
      pending = new PendingAttempt(Instant.now(), List.of());
    }
    String key = executionKey(result);
    Map<String, ExecutionState> states = states(result.getTestContext());
    ExecutionState state = states.computeIfAbsent(key, ignored -> new ExecutionState(result));
    TestNgAttemptResult attempt = state.add(result, status, result.getThrowable(), pending, attemptHistory);
    // TafBaseTest owns the complete neutral hierarchy. Publishing another ServiceLoader-backed
    // result here would create a duplicate, shallow Allure test containing only "Attempt N".
    if (!(result.getInstance() instanceof TafBaseTest)) {
      reportAttempt(state, attempt);
    }

    if (!result.wasRetried()) {
      TestNgExecutionResult completed = state.snapshot();
      results(result.getTestContext()).add(completed);
      resultListeners.forEach(listener -> listener.onResult(completed));
      states.remove(key, state);
    }
  }

  private static boolean isCucumberRunnerInvocation(ITestResult result) {
    Object instance = result.getInstance();
    Class<?> type = instance == null ? result.getTestClass().getRealClass() : instance.getClass();
    while (type != null) {
      // ADR-006 pins this official adapter contract. Exact matching avoids treating unrelated
      // TestNG classes in io.cucumber packages as Cucumber scenario invocations.
      if ("io.cucumber.testng.AbstractTestNGCucumberTests".equals(type.getName())) {
        return true;
      }
      type = type.getSuperclass();
    }
    return false;
  }

  private void reportAttempt(ExecutionState state, TestNgAttemptResult attempt) {
    TafTest test = TafTest.of(state.testName, state.className, stackTrace(attempt.failure()));
    TestStep step =
        new TestStep(
            "Attempt " + attempt.attemptNumber(), attempt.status().name().toLowerCase(), null);
    reporters.forEach(
        reporter -> {
          reporter.beginTest(test);
          if (attempt.failureAnalysis() != null) {
            reporter.reportFailure(attempt.failureAnalysis());
          }
          reporter.reportStep(step);
          attempt.artifacts().forEach(reporter::reportArtifact);
          reporter.endTest(test);
        });
  }

  private static void mergeFailure(ITestResult result, Throwable cleanupFailure) {
    Throwable testFailure = result.getThrowable();
    if (testFailure != null && testFailure != cleanupFailure) {
      testFailure.addSuppressed(cleanupFailure);
    } else {
      result.setThrowable(cleanupFailure);
    }
    result.setStatus(ITestResult.FAILURE);
  }

  private static String executionKey(ITestResult result) {
    return result.getMethod().getQualifiedName()
        + "|"
        + System.identityHashCode(result.getInstance())
        + "|"
        + Arrays.deepToString(result.getParameters())
        + "|"
        + Thread.currentThread().threadId();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, ExecutionState> states(ITestContext context) {
    Object existing = context.getAttribute(STATE_KEY);
    if (existing instanceof Map<?, ?> map) return (Map<String, ExecutionState>) map;
    Map<String, ExecutionState> created = new ConcurrentHashMap<>();
    context.setAttribute(STATE_KEY, created);
    return created;
  }

  @SuppressWarnings("unchecked")
  private static List<TestNgExecutionResult> results(ITestContext context) {
    Object existing = context.getAttribute(RESULTS_KEY);
    if (existing instanceof List<?> list) return (List<TestNgExecutionResult>) list;
    List<TestNgExecutionResult> created = new CopyOnWriteArrayList<>();
    context.setAttribute(RESULTS_KEY, created);
    return created;
  }

  private static String stackTrace(Throwable failure) {
    if (failure == null) {
      return null;
    }
    StringWriter output = new StringWriter();
    failure.printStackTrace(new PrintWriter(output));
    return output.toString();
  }

  private static <T> List<T> load(Class<T> providerType) {
    return ServiceLoader.load(providerType).stream().map(ServiceLoader.Provider::get).toList();
  }

  private record PendingAttempt(
      Instant completedAt,
      List<com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact> artifacts) {}

  private static final class ExecutionState {
    private final String testId;
    private final String testName;
    private final String className;
    private final List<TestNgAttemptResult> attempts = new ArrayList<>();
    private final String executionId = java.util.UUID.randomUUID().toString();

    private ExecutionState(ITestResult result) {
      this.testId = executionKey(result);
      this.testName = result.getMethod().getMethodName();
      this.className = result.getTestClass().getName();
    }

    private synchronized TestNgAttemptResult add(ITestResult nativeResult,
        TestNgAttemptResult.Status status, Throwable failure, PendingAttempt pending,
        AttemptHistoryService history) {
      int number = attempts.size() + 1;
      FailureAnalysis analysis = nativeResult.getAttribute(ANALYSIS_KEY) instanceof FailureAnalysis existing
          ? existing : history.complete(new AttemptHistoryService.AttemptDescriptor(null,
              safeId(className + "." + testName), safeId(executionId), "attempt-" + number,
              pending.completedAt(), outcome(status), Duration.ZERO, null, null, "testng",
              status == TestNgAttemptResult.Status.CONFIGURATION_FAILED ? "configuration" : "test",
              status == TestNgAttemptResult.Status.CONFIGURATION_FAILED ? FailureContext.Boundary.AUTOMATION
                  : FailureContext.Boundary.UNKNOWN, failure, List.of())).analysis();
      TestNgAttemptResult attempt =
          new TestNgAttemptResult(
              number, pending.completedAt(), status, failure, pending.artifacts(), analysis);
      attempts.add(attempt);
      return attempt;
    }

    private synchronized TestNgExecutionResult snapshot() {
      return new TestNgExecutionResult(testId, testName, className, attempts);
    }
  }

  private static ExecutionAttemptSummary.Outcome outcome(TestNgAttemptResult.Status status) {
    return switch (status) {
      case PASSED -> ExecutionAttemptSummary.Outcome.PASSED;
      case FAILED, CONFIGURATION_FAILED -> ExecutionAttemptSummary.Outcome.FAILED;
      case SKIPPED -> ExecutionAttemptSummary.Outcome.SKIPPED;
    };
  }

  private static String safeId(String value) {
    try {
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  private static AttemptHistoryService disabledHistory() {
    var configuration = new HistoryConfiguration(false, Path.of("target", "taf-evidence", "history"),
        500, Duration.ofDays(30), 50L * 1024 * 1024, HistoryConfiguration.UnavailabilityPolicy.CONTINUE,
        HistoryConfiguration.CorruptionPolicy.REPORT);
    return new AttemptHistoryService(new FailureClassificationService(), new FailureSignatureService(),
        new DisabledExecutionHistoryRepository(), configuration, 2);
  }
}
