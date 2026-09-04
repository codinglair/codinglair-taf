package com.codinglair.taf.runtime.cucumber;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.lifecycle.InvocationDescriptor;
import com.codinglair.taf.runtime.core.lifecycle.InvocationOutcome;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.HierarchicalReport;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.ReporterDispatcher;
import com.codinglair.taf.runtime.core.reporting.ReportingActionInterceptor;
import com.codinglair.taf.runtime.core.reporting.ReportingContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.definition.TestDefinition;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import io.cucumber.java.Scenario;
import io.cucumber.java.Status;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/** Scenario-scoped access to the Runtime session used by Cucumber glue. */
public final class CucumberScenarioSession implements AutoCloseable {
  private static final String TEST_CASE_TAG_PREFIX = "@test-case-";
  private TestSession session;
  private Scenario scenario;
  private InvocationDescriptor invocation;
  private final TestSessionLifecycle lifecycle;
  private final CurrentReportingContext currentReporting;
  private final List<TestReporter> reporters;
  private ReportingState reporting;

  public CucumberScenarioSession() {
    this(
        new TestSessionLifecycle(TestSession::create),
        new CurrentReportingContext(),
        loadReporters());
  }

  CucumberScenarioSession(TestSessionLifecycle lifecycle) {
    this(lifecycle, new CurrentReportingContext(), List.of());
  }

  public CucumberScenarioSession(
      TestSessionLifecycle lifecycle,
      CurrentReportingContext currentReporting,
      List<TestReporter> reporters) {
    this.lifecycle = Objects.requireNonNull(lifecycle);
    this.currentReporting = Objects.requireNonNull(currentReporting);
    this.reporters = List.copyOf(reporters);
  }

  void start(Scenario scenario) {
    if (session != null) {
      throw new IllegalStateException("A TestSession is already active for this Cucumber scenario");
    }
    this.scenario = Objects.requireNonNull(scenario, "scenario");
    invocation =
        new InvocationDescriptor(
            scenario.getId(), scenario.getName(), TafCucumberHooks.class.getName());
    reporting = openReporting(scenario);
    currentReporting.bind(reporting.context);
    try {
      session = lifecycle.open(invocation);
      session
          .getControllerRegistry()
          .enableReporting(new ReportingActionInterceptor(currentReporting));
    } catch (Throwable failure) {
      TestSession partial = session;
      session = null;
      if (partial != null) {
        try {
          lifecycle.close(invocation, InvocationOutcome.setupFailed(failure));
        } catch (Throwable cleanup) {
          failure.addSuppressed(cleanup);
        }
      }
      reporting.scope.fail(message(failure));
      finishReporting(partial);
      currentReporting.unbind();
      reporting = null;
      this.scenario = null;
      invocation = null;
      throwUnchecked(failure);
    }
  }

  /** Returns all effective feature/scenario tags reported by Cucumber. */
  public Collection<String> tags() {
    return java.util.List.copyOf(scenario().getSourceTagNames());
  }

  /**
   * Resolves the optional stable test-case ID from an {@code @test-case-<id>} tag. Multiple IDs are
   * rejected because traceability would otherwise be ambiguous.
   */
  public Optional<String> testCaseId() {
    var identifiers =
        tags().stream()
            .filter(tag -> tag.startsWith(TEST_CASE_TAG_PREFIX))
            .map(tag -> tag.substring(TEST_CASE_TAG_PREFIX.length()).trim())
            .filter(identifier -> !identifier.isEmpty())
            .distinct()
            .toList();
    if (identifiers.size() > 1) {
      throw new IllegalStateException("Multiple Cucumber test-case ID tags found: " + identifiers);
    }
    return identifiers.stream().findFirst();
  }

  /**
   * Resolves this scenario's typed definition through the same runner-neutral resolver as TestNG.
   */
  public <I, E> TestDefinition<I, E> testDefinition(
      TestDefinitionResolver resolver, Class<I> inputType, Class<E> expectedOutputType) {
    String id =
        testCaseId()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No @test-case-<id> tag is declared for the current Cucumber scenario"));
    return Objects.requireNonNull(resolver, "resolver").require(id, inputType, expectedOutputType);
  }

  /** Attaches textual business evidence to the currently executing Gherkin step. */
  public void attach(String content, String mediaType, String name) {
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(mediaType, "mediaType");
    scenario().attach(content, mediaType, name);
  }

  /** Attaches binary business evidence to the currently executing Gherkin step. */
  public void attach(byte[] content, String mediaType, String name) {
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(mediaType, "mediaType");
    scenario().attach(content.clone(), mediaType, name);
  }

  /** Returns the active scenario session. */
  public TestSession session() {
    if (session == null) {
      throw new IllegalStateException("No TestSession is active for this Cucumber scenario");
    }
    return session;
  }

  /** Completes the active session. Repeated cleanup is safe. */
  @Override
  public void close() {
    TestSession active = session;
    session = null;
    Scenario completedScenario = scenario;
    scenario = null;
    InvocationDescriptor completedInvocation = invocation;
    invocation = null;
    if (active != null) {
      InvocationOutcome outcome = outcome(completedScenario);
      Throwable failure = null;
      try {
        lifecycle.close(completedInvocation, outcome);
      } catch (Throwable current) {
        failure = current;
      } finally {
        if (reporting != null) {
          if (failure == null && completedScenario.getStatus() == Status.PASSED)
            reporting.scope.pass();
          else
            reporting.scope.fail(
                failure == null ? completedScenario.getStatus().name() : message(failure));
          finishReporting(active);
          reporting = null;
        }
        currentReporting.unbind();
      }
      if (failure != null) throwUnchecked(failure);
    }
  }

  private static InvocationOutcome outcome(Scenario scenario) {
    if (scenario == null) {
      return InvocationOutcome.setupFailed(
          new IllegalStateException("Cucumber scenario setup did not complete"));
    }
    Status status = scenario.getStatus();
    if (status == Status.PASSED) {
      return InvocationOutcome.passed();
    }
    AssertionError failure =
        new AssertionError(
            "Cucumber scenario "
                + status.name().toLowerCase(java.util.Locale.ROOT)
                + ": "
                + scenario.getName());
    return status == Status.FAILED
        ? InvocationOutcome.failed(failure)
        : InvocationOutcome.cancelled(failure);
  }

  private Scenario scenario() {
    if (scenario == null) {
      throw new IllegalStateException("No Cucumber scenario is active");
    }
    return scenario;
  }

  private ReportingState openReporting(Scenario scenario) {
    TafTest test = TafTest.of(scenario.getName(), TafCucumberHooks.class.getName());
    var dispatchers =
        reporters.stream()
            .map(reporter -> new ReporterDispatcher(reporter, new RedactionPipeline()))
            .toList();
    dispatchers.forEach(dispatcher -> dispatcher.beginTest(test));
    var hierarchy = new HierarchicalReport(scenario.getId(), new RedactionPipeline());
    return new ReportingState(
        test,
        new ReportingContext(hierarchy, dispatchers),
        hierarchy.open(ReportLevel.SCENARIO, scenario.getName()),
        dispatchers);
  }

  private void finishReporting(TestSession completedSession) {
    reporting.context.flush();
    if (completedSession != null) {
      completedSession.getArtifactCollector().finalizeEvidenceForEach(reporting.dispatchers);
    }
    reporting.dispatchers.forEach(dispatcher -> dispatcher.endTest(reporting.test));
  }

  private static List<TestReporter> loadReporters() {
    return ServiceLoader.load(TestReporter.class).stream()
        .map(ServiceLoader.Provider::get)
        .toList();
  }

  private static String message(Throwable failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }

  private static void throwUnchecked(Throwable failure) {
    CucumberScenarioSession.<RuntimeException>throwAny(failure);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }

  private record ReportingState(
      TafTest test,
      ReportingContext context,
      HierarchicalReport.Scope scope,
      List<ReporterDispatcher> dispatchers) {}
}
