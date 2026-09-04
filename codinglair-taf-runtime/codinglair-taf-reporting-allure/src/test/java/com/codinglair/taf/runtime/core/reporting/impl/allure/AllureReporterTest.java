package com.codinglair.taf.runtime.core.reporting.impl.allure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.HierarchicalReport;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.ReporterDispatcher;
import com.codinglair.taf.runtime.core.reporting.ReportingActionInterceptor;
import com.codinglair.taf.runtime.core.reporting.ReportingBeanPostProcessor;
import com.codinglair.taf.runtime.core.reporting.ReportingContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.FileSystemResultsWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllureReporterTest {

  @TempDir Path resultsDirectory;

  @Test
  void isDiscoverableThroughVendorNeutralReporterSpi() {
    assertThat(ServiceLoader.load(TestReporter.class))
        .anySatisfy(reporter -> assertThat(reporter).isInstanceOf(AllureReporter.class));
  }

  @Test
  void writesLifecycleResultWithStepsAndSanitizedAttachments() throws IOException {
    AllureReporter reporter = reporter();
    TafTest test = TafTest.of("checkout", "example.CheckoutTest");

    reporter.beginTest(test);
    reporter.reportStep(TestStep.of("submit order", "passed", "business action"));
    reporter.reportArtifact(
        TestArtifact.of("request", "payload", "password=hunter2", "text/plain"));
    reporter.reportArtifact(TestArtifact.of("response", "payload", "ok", "text/plain"));
    reporter.endTest(test);

    String results = readResults();
    assertThat(results)
        .contains("checkout", "submit order", "business action", "request", "response");
    assertThat(results).doesNotContain("hunter2");
    assertThat(reporter.reportedSteps()).isOne();
    assertThat(reporter.reportedArtifacts()).isEqualTo(2);
  }

  @Test
  void writesSeparateResultsForRerunAttempts() throws IOException {
    AllureReporter reporter = reporter();
    TafTest attempt = TafTest.of("retryable test", "example.RetryTest");

    reporter.beginTest(attempt);
    reporter.endTest(attempt);
    reporter.beginTest(attempt);
    reporter.endTest(attempt);

    try (var files = Files.list(resultsDirectory)) {
      assertThat(files.filter(path -> path.getFileName().toString().endsWith("-result.json")))
          .hasSize(2);
    }
  }

  @Test
  void mapsNeutralHierarchyAndSuppressesDuplicateEvents() throws IOException {
    AllureReporter reporter = reporter();
    var hierarchy = new HierarchicalReport("scenario-1", new RedactionPipeline());
    try (var workflow = hierarchy.open("workflow", ReportLevel.WORKFLOW, "purchase", null)) {
      try (var action = hierarchy.open("action", ReportLevel.CONSUMER_ACTION, "submit", null)) {}
    }
    reporter.beginTest(TafTest.of("checkout", "example.CheckoutTest"));
    hierarchy.events().forEach(reporter::reportEvent);
    hierarchy.events().forEach(reporter::reportEvent);
    reporter.endTest(TafTest.of("checkout", "example.CheckoutTest"));

    assertThat(readResults()).contains("purchase", "submit");
    assertThat(reporter.reportedSteps()).isEqualTo(2);
  }

  @Test
  void goldenOutputContainsEachRequiredConsumerLevelOnce() throws IOException {
    AllureReporter reporter = reporter();
    var hierarchy = new HierarchicalReport("golden", new RedactionPipeline());
    List<ReportLevel> levels =
        List.of(
            ReportLevel.WORKFLOW,
            ReportLevel.PAGE,
            ReportLevel.COMPONENT,
            ReportLevel.API,
            ReportLevel.SCREEN,
            ReportLevel.BDD_STEP,
            ReportLevel.CONSUMER_ACTION,
            ReportLevel.CONTROLLER_OPERATION,
            ReportLevel.VALIDATION);
    var scopes = new ArrayList<HierarchicalReport.Scope>();
    for (ReportLevel level : levels)
      scopes.add(hierarchy.open(level.name(), level, level.name(), null));
    for (int index = scopes.size() - 1; index >= 0; index--) scopes.get(index).close();
    reporter.beginTest(TafTest.of("golden", "example.GoldenReportTest"));
    hierarchy.events().forEach(reporter::reportEvent);
    reporter.endTest(TafTest.of("golden", "example.GoldenReportTest"));

    String output = readResults();
    for (ReportLevel level : levels) {
      assertThat(output).containsOnlyOnce("\"name\":\"" + level.name() + "\"");
    }
  }

  @Test
  void automaticInterceptionProducesSanitizedAllureHierarchyJson() throws Exception {
    AllureReporter reporter = reporter();
    TafTest test = TafTest.of("automatic", "example.AutomaticTest");
    reporter.beginTest(test);
    var current = new CurrentReportingContext();
    var hierarchy = new HierarchicalReport("automatic", new RedactionPipeline());
    var context =
        new ReportingContext(
            hierarchy, List.of(new ReporterDispatcher(reporter, new RedactionPipeline())));
    current.bind(context);
    try {
      var page =
          (AutomaticPage)
              new ReportingBeanPostProcessor(new ReportingActionInterceptor(current))
                  .postProcessAfterInitialization(new AutomaticPage(), "automaticPage");
      page.submit("token=allure-secret");
    } finally {
      current.unbind();
      reporter.endTest(test);
    }

    String output = readResults();
    assertThat(output).containsOnlyOnce("\"name\":\"Submit order\"");
    assertThat(output).contains("\"status\":\"passed\"");
    assertThat(output).doesNotContain("allure-secret");
  }

  @Test
  void parallelInvocationsDoNotClearAnotherInvocationsHierarchy() throws Exception {
    AllureReporter reporter = reporter();
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          java.util.stream.IntStream.range(0, 8)
              .<java.util.concurrent.Callable<Void>>mapToObj(
                  index ->
                      () -> {
                        TafTest test = TafTest.of("parallel-" + index, "example.ParallelTest");
                        reporter.beginTest(test);
                        var hierarchy =
                            new HierarchicalReport("parallel-" + index, new RedactionPipeline());
                        try (var scope = hierarchy.open(ReportLevel.PAGE, "page-" + index)) {}
                        hierarchy.events().forEach(reporter::reportEvent);
                        reporter.endTest(test);
                        return null;
                      })
              .toList();
      for (var result : executor.invokeAll(tasks)) result.get();
    }
    String output = readResults();
    for (int index = 0; index < 8; index++) {
      assertThat(output).contains("parallel-" + index, "page-" + index);
    }
  }

  @Test
  void rejectsEventsOutsideAnActiveTest() {
    AllureReporter reporter = reporter();

    assertThatThrownBy(() -> reporter.reportStep(TestStep.of("orphan", "passed")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> reporter.reportArtifact(TestArtifact.of("orphan", "text", "value")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failedTestIsMappedToFailedStatus() throws IOException {
    AllureReporter reporter = reporter();
    TafTest failed = TafTest.of("failure", "example.FailureTest", "boom");

    reporter.beginTest(failed);
    reporter.endTest(failed);

    assertThat(readResults()).contains("\"status\":\"failed\"");
  }

  @Test
  void mapsAuthoritativeFailureAnalysisWithoutReclassification() throws IOException {
    RuntimeException failure = new RuntimeException("synthetic failure token=contract-canary");
    failure.setStackTrace(new StackTraceElement[] {new StackTraceElement("sample.Controller", "execute", "Controller.java", 42)});
    FailureAnalysis analysis = FailureAnalysis.classify("web", "action", FailureContext.Boundary.UNKNOWN, failure);
    assertThat(analysis.signature().value()).isEqualTo("failure-signature:v1:9177a40eb1d9f7a5a2731cae8cb9e08a9d276b5b73e3af19114a1097fb5c3439");
    AllureReporter reporter = reporter();
    reporter.beginTest(TafTest.of("synthetic", "example.Synthetic"));
    reporter.reportFailure(analysis);
    reporter.endTest(TafTest.of("synthetic", "example.Synthetic", "failed"));
    String output = readResults();
    assertThat(output).contains("taf.failure.classification", analysis.classification().type().name(),
        "taf.failure.source", analysis.classification().source().name(), "taf.failure.signature",
        analysis.signature().value(), "taf.failure.stability", analysis.stability().name());
    assertThat(output).doesNotContain("contract-canary");
  }

  private AllureReporter reporter() {
    return new AllureReporter(
        new AllureLifecycle(new FileSystemResultsWriter(resultsDirectory)),
        new RedactionPipeline());
  }

  private String readResults() throws IOException {
    StringBuilder content = new StringBuilder();
    try (var files = Files.list(resultsDirectory)) {
      for (Path file : files.toList()) {
        if (file.getFileName().toString().endsWith(".json")) {
          content.append(Files.readString(file));
        }
      }
    }
    return content.toString();
  }

  static class AutomaticPage {
    @PageAction("Submit order")
    public void submit(String credential) {}
  }
}
