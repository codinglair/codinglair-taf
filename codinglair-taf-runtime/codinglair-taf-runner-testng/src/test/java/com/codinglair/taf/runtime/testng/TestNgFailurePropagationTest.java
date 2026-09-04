package com.codinglair.taf.runtime.testng;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.core.Error.ErrorType;
import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testng.annotations.BeforeMethod;

class TestNgFailurePropagationTest {
  @Test
  void jsonCarriesTheExactAuthoritativeAnalysis() {
    FailureAnalysis analysis = synthetic();
    assertThat(analysis.signature().value()).isEqualTo("failure-signature:v1:9177a40eb1d9f7a5a2731cae8cb9e08a9d276b5b73e3af19114a1097fb5c3439");
    var attempt = new TestNgAttemptResult(1, Instant.EPOCH, TestNgAttemptResult.Status.FAILED,
        new RuntimeException("synthetic"), List.of(), analysis);
    String json = new TestNgStructuredResultWriter().writeJson(
        new TestNgExecutionResult("test", "synthetic", "Example", List.of(attempt)));
    assertThat(json).contains(analysis.classification().type().name(), analysis.classification().source().name(),
        analysis.signature().value(), analysis.signature().algorithm(), analysis.stability().name(),
        analysis.historyStatus().name()).doesNotContain("contract-canary");
  }

  @Test
  void configurationFailureIsClassifiedPreservedAndReported() {
    var results = new ArrayList<TestNgExecutionResult>();
    var reporter = new CapturingReporter();
    var testng = new org.testng.TestNG();
    testng.setUseDefaultListeners(false);
    testng.addListener(new TestNgLifecycleListener(List.of(reporter), List.of(results::add)));
    testng.setTestClasses(new Class<?>[] {ConfigurationFailure.class});
    testng.run();
    assertThat(results).flatExtracting(TestNgExecutionResult::attempts)
        .anySatisfy(attempt -> {
          assertThat(attempt.status()).isEqualTo(TestNgAttemptResult.Status.CONFIGURATION_FAILED);
          assertThat(attempt.failureAnalysis()).isNotNull();
          assertThat(attempt.failureAnalysis().classification().type()).isEqualTo(ErrorType.AUTOMATION_FAILURE);
          assertThat(attempt.failureAnalysis().signature()).isNotNull();
        });
    assertThat(reporter.failures).isNotEmpty();
  }

  static FailureAnalysis synthetic() {
    RuntimeException failure = new RuntimeException("synthetic failure token=contract-canary");
    failure.setStackTrace(new StackTraceElement[] {new StackTraceElement("sample.Controller", "execute", "Controller.java", 42)});
    return FailureAnalysis.classify("web", "action", FailureContext.Boundary.UNKNOWN, failure);
  }

  public static final class ConfigurationFailure {
    @BeforeMethod public void setup() { throw new IllegalStateException("configuration failed"); }
    @org.testng.annotations.Test public void neverRuns() {}
  }

  private static final class CapturingReporter implements TestReporter {
    private final List<FailureAnalysis> failures = new ArrayList<>();
    public void reportFailure(FailureAnalysis analysis) { failures.add(analysis); }
    public void beginTest(TafTest test) {}
    public void endTest(TafTest test) {}
    public void reportStep(TestStep step) {}
    public void reportArtifact(TestArtifact artifact) {}
    public String getName() { return "capture"; }
  }
}
