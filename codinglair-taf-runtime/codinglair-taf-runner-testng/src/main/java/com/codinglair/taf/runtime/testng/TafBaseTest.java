package com.codinglair.taf.runtime.testng;

import com.codinglair.taf.core.annotation.reporting.TafDescription;
import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.context.TestContext;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.lifecycle.InvocationDescriptor;
import com.codinglair.taf.runtime.core.lifecycle.InvocationOutcome;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.core.history.AttemptHistoryService;
import com.codinglair.taf.runtime.core.history.ExecutionAttemptSummary;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.CurrentReportingContext;
import com.codinglair.taf.runtime.core.reporting.HierarchicalReport;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.ReporterDispatcher;
import com.codinglair.taf.runtime.core.reporting.ReportingContext;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.definition.TestDefinition;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/** Sole session lifecycle owner for ordinary TestNG test-method invocations. */
@ContextConfiguration(classes = TafRuntimeAutoConfiguration.class)
public abstract class TafBaseTest extends AbstractTestNGSpringContextTests {
  @Autowired private TestSessionLifecycle lifecycle;
  @Autowired private ConsumerPreflight preflight;
  @Autowired private ObjectProvider<TestDefinitionResolver> definitionResolvers;
  @Autowired private ObjectProvider<TestReporter> reporters;
  @Autowired private CurrentReportingContext currentReporting;
  @Autowired private AttemptHistoryService attemptHistory;
  private final ThreadLocal<InvocationDescriptor> invocation = new ThreadLocal<>();
  private final ThreadLocal<InvocationMetadata> metadata = new ThreadLocal<>();
  private final ThreadLocal<ReportingState> reporting = new ThreadLocal<>();

  @BeforeMethod(alwaysRun = true)
  public final void openTafSession(ITestResult result) {
    InvocationDescriptor descriptor = SessionFactory.descriptor(result);
    TestSession session = lifecycle.open(descriptor);
    invocation.set(descriptor);
    try {
      metadata.set(resolveMetadata(result));
      reporting.set(openReporting(result, session));
      currentReporting.bind(reporting.get().context);
      preflight.verify();
      SessionFactory.bindObservation(result, session);
    } catch (Throwable setupFailure) {
      closeAfterSetupFailure(result, descriptor, setupFailure);
      throwUnchecked(setupFailure);
    }
  }

  @AfterMethod(alwaysRun = true)
  public final void closeTafSession(ITestResult result) {
    InvocationDescriptor descriptor = invocation.get();
    if (descriptor == null) return;
    Throwable cleanupFailure = null;
    try {
      try {
        lifecycle.close(descriptor, outcome(result));
      } catch (Throwable failure) {
        cleanupFailure = failure;
        mergeFailure(result, failure);
        result.setStatus(ITestResult.FAILURE);
      }
    } finally {
      recordAttempt(result, descriptor, false);
      finishReporting(result);
      invocation.remove();
      metadata.remove();
      reporting.remove();
      currentReporting.unbind();
      SessionFactory.unbindObservation(result);
    }
    if (cleanupFailure != null) throwUnchecked(cleanupFailure);
  }

  /** Returns the session already bound to the current TestNG method invocation. */
  protected final TestSession testSession() {
    return lifecycle.require();
  }

  /** Returns the transient context owned by the current session. */
  protected final TestContext testContext() {
    return testSession().getTestContext();
  }

  /** Returns the method-level test-case ID, falling back to the test class annotation. */
  protected final String testCaseId() {
    return currentMetadata()
        .testCaseId()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No @TestCaseId is declared for the current TestNG invocation"));
  }

  /** Returns the optional description resolved from the current test method. */
  protected final Optional<String> tafDescription() {
    return currentMetadata().description();
  }

  /** Resolves this invocation's typed definition through the project-authoritative repository. */
  protected final <I, E> TestDefinition<I, E> testDefinition(
      Class<I> inputType, Class<E> expectedOutputType) {
    return definitionResolver().require(testCaseId(), inputType, expectedOutputType);
  }

  /** Returns the typed input bound to the active {@link TestCaseId}. */
  protected final <T> T testInput(Class<T> type) {
    return definitionResolver().requireInput(testCaseId(), type);
  }

  /** Returns the typed expected output bound to the active {@link TestCaseId}. */
  protected final <T> T expectedOutput(Class<T> type) {
    return definitionResolver().requireExpectedOutput(testCaseId(), type);
  }

  /** Records one neutral composite consumer action. */
  protected final void step(String name, Runnable action) {
    java.util.Objects.requireNonNull(action, "action");
    try {
      runStep(name, action::run);
    } catch (RuntimeException | Error failure) {
      throw failure;
    } catch (Exception impossible) {
      throw new IllegalStateException("Runnable step raised a checked exception", impossible);
    }
  }

  /** Records one neutral composite consumer action that may throw a checked exception. */
  protected final void stepThrowing(String name, ThrowingAction action) throws Exception {
    runStep(name, java.util.Objects.requireNonNull(action, "action"));
  }

  /** Resolves a typed, named controller from the current session. */
  protected final <T extends TestController> T controller(Class<T> type, String name) {
    return testSession().getController(type, name);
  }

  private void closeAfterSetupFailure(ITestResult result, InvocationDescriptor descriptor, Throwable setupFailure) {
    try {
      lifecycle.close(descriptor, InvocationOutcome.setupFailed(setupFailure));
    } finally {
      result.setThrowable(setupFailure);
      recordAttempt(result, descriptor, true);
      finishReportingFailure(result, setupFailure);
      invocation.remove();
      metadata.remove();
      reporting.remove();
      currentReporting.unbind();
    }
  }

  private TestDefinitionResolver definitionResolver() {
    TestDefinitionResolver resolver = definitionResolvers.getIfAvailable();
    if (resolver == null) {
      throw new IllegalStateException(
          "No TestDefinitionResolver is configured for test case '"
              + testCaseId()
              + "'; configure one TestDefinitionRepository provider");
    }
    return resolver;
  }

  private ReportingState openReporting(ITestResult result, TestSession session) {
    TafTest test = TafTest.of(result.getMethod().getMethodName(), result.getTestClass().getName());
    List<ReporterDispatcher> dispatchers =
        reporters
            .orderedStream()
            .map(reporter -> new ReporterDispatcher(reporter, new RedactionPipeline()))
            .toList();
    dispatchers.forEach(dispatcher -> dispatcher.beginTest(test));
    HierarchicalReport hierarchy =
        new HierarchicalReport(
            session.getCorrelationContext().getTraceId(), new RedactionPipeline());
    return new ReportingState(
        test,
        hierarchy,
        new ReportingContext(hierarchy, dispatchers),
        hierarchy.open(ReportLevel.TEST, test.name()),
        dispatchers,
        session.getArtifactCollector());
  }

  private void runStep(String name, ThrowingAction action) throws Exception {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("step name must not be blank");
    ReportingState state = requireReporting();
    state.context.execute(
        ReportLevel.CONSUMER_ACTION,
        name,
        () -> {
          action.run();
          return null;
        });
  }

  private ReportingState requireReporting() {
    ReportingState state = reporting.get();
    if (state == null)
      throw new IllegalStateException("No TestNG reporting scope is currently open");
    return state;
  }

  private void finishReporting(ITestResult result) {
    ReportingState state = reporting.get();
    if (state == null) return;
    Throwable failure = result.getThrowable();
    if (failure == null && result.getStatus() != ITestResult.SKIP) state.testScope.pass();
    else state.testScope.fail(failure == null ? "TestNG invocation skipped" : failure.toString());
    state.flush();
    if (result.getAttribute(TestNgLifecycleListener.ANALYSIS_KEY)
        instanceof com.codinglair.taf.runtime.core.failure.FailureAnalysis analysis) {
      state.dispatchers.forEach(dispatcher -> dispatcher.reportFailure(analysis));
    }
    testSessionArtifacts(state);
    state.dispatchers.forEach(dispatcher -> dispatcher.endTest(state.test));
  }

  private void finishReportingFailure(ITestResult result, Throwable failure) {
    ReportingState state = reporting.get();
    if (state == null) return;
    state.testScope.fail(failure.toString());
    state.flush();
    if (result.getAttribute(TestNgLifecycleListener.ANALYSIS_KEY)
        instanceof com.codinglair.taf.runtime.core.failure.FailureAnalysis analysis) {
      state.dispatchers.forEach(dispatcher -> dispatcher.reportFailure(analysis));
    }
    testSessionArtifacts(state);
    state.dispatchers.forEach(dispatcher -> dispatcher.endTest(state.test));
  }

  private void recordAttempt(ITestResult result, InvocationDescriptor descriptor, boolean setupFailure) {
    Throwable failure = result.getThrowable();
    ExecutionAttemptSummary.Outcome outcome = failure != null ? ExecutionAttemptSummary.Outcome.FAILED
        : result.getStatus() == ITestResult.SKIP ? ExecutionAttemptSummary.Outcome.SKIPPED
        : ExecutionAttemptSummary.Outcome.PASSED;
    var completion = attemptHistory.complete(new AttemptHistoryService.AttemptDescriptor(null,
        hash(result.getTestClass().getName() + "." + result.getMethod().getMethodName()
            + java.util.Arrays.deepToString(result.getParameters())),
        hash(result.getTestContext().getSuite().getName() + ":" + result.getTestContext().getStartDate()
            + ":" + result.getTestClass().getName() + "." + result.getMethod().getMethodName()),
        "attempt-" + Math.max(1L, result.getStartMillis()) + "-" + System.identityHashCode(result),
        java.time.Instant.now(), outcome,
        java.time.Duration.ofMillis(Math.max(0L, result.getEndMillis() - result.getStartMillis())),
        null, null, "testng", setupFailure ? "configuration" : "test",
        setupFailure ? FailureContext.Boundary.AUTOMATION : FailureContext.Boundary.UNKNOWN,
        failure, List.of()));
    if (completion.analysis() != null) result.setAttribute(TestNgLifecycleListener.ANALYSIS_KEY, completion.analysis());
  }

  private static String hash(String value) {
    try {
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  private void testSessionArtifacts(ReportingState state) {
    state.artifacts.finalizeEvidenceForEach(state.dispatchers);
  }

  private static InvocationMetadata resolveMetadata(ITestResult result) {
    Method method = result.getMethod().getConstructorOrMethod().getMethod();
    TestCaseId methodId = AnnotatedElementUtils.findMergedAnnotation(method, TestCaseId.class);
    TestCaseId classId =
        AnnotatedElementUtils.findMergedAnnotation(
            result.getTestClass().getRealClass(), TestCaseId.class);
    TafDescription description =
        AnnotatedElementUtils.findMergedAnnotation(method, TafDescription.class);
    return new InvocationMetadata(
        Optional.ofNullable(
            methodId != null ? methodId.value() : classId == null ? null : classId.value()),
        Optional.ofNullable(description).map(TafDescription::value));
  }

  private InvocationMetadata currentMetadata() {
    InvocationMetadata current = metadata.get();
    if (current == null)
      throw new IllegalStateException("No TestNG invocation metadata is currently bound");
    return current;
  }

  private static InvocationOutcome outcome(ITestResult result) {
    Throwable failure = result.getThrowable();
    if (failure instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
      return InvocationOutcome.cancelled(
          failure == null ? new InterruptedException("Invocation interrupted") : failure);
    }
    if (result.getStatus() == ITestResult.SKIP) {
      return InvocationOutcome.cancelled(
          failure == null ? new IllegalStateException("TestNG invocation skipped") : failure);
    }
    return failure == null ? InvocationOutcome.passed() : InvocationOutcome.failed(failure);
  }

  private static void mergeFailure(ITestResult result, Throwable failure) {
    Throwable existing = result.getThrowable();
    if (existing == null) result.setThrowable(failure);
    else if (existing != failure) existing.addSuppressed(failure);
  }

  private static void throwUnchecked(Throwable failure) {
    TafBaseTest.<RuntimeException>throwAny(failure);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }

  private record InvocationMetadata(Optional<String> testCaseId, Optional<String> description) {}

  @FunctionalInterface
  protected interface ThrowingAction {
    void run() throws Exception;
  }

  private static final class ReportingState {
    private final TafTest test;
    private final HierarchicalReport hierarchy;
    private final ReportingContext context;
    private final HierarchicalReport.Scope testScope;
    private final List<ReporterDispatcher> dispatchers;
    private final ArtifactCollector artifacts;

    private ReportingState(
        TafTest test,
        HierarchicalReport hierarchy,
        ReportingContext context,
        HierarchicalReport.Scope testScope,
        List<ReporterDispatcher> dispatchers,
        ArtifactCollector artifacts) {
      this.test = test;
      this.hierarchy = hierarchy;
      this.context = context;
      this.testScope = testScope;
      this.dispatchers = dispatchers;
      this.artifacts = artifacts;
    }

    private void flush() {
      context.flush();
    }
  }
}
