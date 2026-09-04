package com.codinglair.taf.runtime.core.reporting.impl.allure;

import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.reporting.RedactionService;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Maps the vendor-neutral TAF reporting SPI to an Allure lifecycle. */
public final class AllureReporter implements TestReporter {

  private final AllureLifecycle lifecycle;
  private final RedactionService redactionService;
  private final ThreadLocal<InvocationState> current = new ThreadLocal<>();
  private final AtomicInteger stepCount = new AtomicInteger();
  private final AtomicInteger artifactCount = new AtomicInteger();

  public AllureReporter() {
    this(Allure.getLifecycle(), new RedactionPipeline());
  }

  AllureReporter(AllureLifecycle lifecycle, RedactionService redactionService) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.redactionService = Objects.requireNonNull(redactionService, "redactionService");
  }

  @Override
  public void beginTest(TafTest test) {
    Objects.requireNonNull(test, "test");
    if (current.get() != null) {
      throw new IllegalStateException("A test is already active on this thread");
    }
    String uuid = UUID.randomUUID().toString();
    TestResult result =
        new TestResult()
            .setUuid(uuid)
            .setName(displayName(test))
            .setFullName(test.className())
            .setStatus(Status.PASSED);
    lifecycle.scheduleTestCase(uuid, result);
    lifecycle.startTestCase(uuid);
    current.set(new InvocationState(uuid));
  }

  @Override
  public void endTest(TafTest test) {
    Objects.requireNonNull(test, "test");
    String uuid = requireActiveTest();
    try {
      if (test.stackTrace() != null && !test.stackTrace().isBlank()) {
        lifecycle.updateTestCase(uuid, result -> result.setStatus(Status.FAILED));
      }
      lifecycle.stopTestCase(uuid);
      lifecycle.writeTestCase(uuid);
    } finally {
      current.remove();
    }
  }

  @Override
  public void reportStep(TestStep step) {
    Objects.requireNonNull(step, "step");
    String testUuid = requireActiveTest();
    String stepUuid = UUID.randomUUID().toString();
    StepResult result =
        new StepResult()
            .setName(Objects.requireNonNullElse(step.name(), "Unnamed step"))
            .setDescription(step.description())
            .setStatus(toStatus(step.status()));
    lifecycle.startStep(testUuid, stepUuid, result);
    lifecycle.stopStep(stepUuid);
    stepCount.incrementAndGet();
  }

  @Override
  public void reportFailure(FailureAnalysis analysis) {
    Objects.requireNonNull(analysis, "analysis");
    String uuid = requireActiveTest();
    lifecycle.updateTestCase(uuid, result -> {
      result.getLabels().add(new Label().setName("taf.failure.classification")
          .setValue(analysis.classification().type().name()));
      result.getLabels().add(new Label().setName("taf.failure.source")
          .setValue(analysis.classification().source().name()));
      result.getLabels().add(new Label().setName("taf.failure.stability")
          .setValue(analysis.stability().name()));
      result.getLabels().add(new Label().setName("taf.failure.history")
          .setValue(analysis.historyStatus().name()));
      if (analysis.signature() != null) {
        result.getLabels().add(new Label().setName("taf.failure.signature")
            .setValue(analysis.signature().value()));
        result.getLabels().add(new Label().setName("taf.failure.signature.algorithm")
            .setValue(analysis.signature().algorithm()));
      }
    });
  }

  @Override
  public void reportEvent(ReportEvent event) {
    Objects.requireNonNull(event, "event");
    InvocationState state = requireState();
    String testUuid = state.testId;
    if (event.phase() == ReportEvent.Phase.STARTED) {
      if (state.completedEvents.contains(event.id()) || state.stepIds.containsKey(event.id()))
        return;
      String uuid = UUID.randomUUID().toString();
      if (state.stepIds.putIfAbsent(event.id(), uuid) != null) return;
      StepResult result =
          new StepResult()
              .setName(redactionService.redact(event.name()))
              .setDescription(redactionService.redact(event.description()));
      String parentUuid =
          event.parentId() == null
              ? testUuid
              : state.stepIds.getOrDefault(event.parentId(), testUuid);
      lifecycle.startStep(parentUuid, uuid, result);
      stepCount.incrementAndGet();
      return;
    }
    if (!state.completedEvents.add(event.id())) return;
    String uuid = state.stepIds.remove(event.id());
    Status status = toStatus(event.status());
    if (event.level() == com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel.TEST
        || event.level()
            == com.codinglair.taf.runtime.core.reporting.abstraction.ReportLevel.SCENARIO) {
      lifecycle.updateTestCase(testUuid, result -> result.setStatus(status));
    }
    if (uuid != null) {
      lifecycle.updateStep(uuid, result -> result.setStatus(status));
      lifecycle.stopStep(uuid);
    }
  }

  @Override
  public void reportArtifact(TestArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    requireActiveTest();
    String content = Objects.requireNonNullElse(artifact.content(), "");
    lifecycle.addAttachment(
        Objects.requireNonNullElse(artifact.name(), "artifact"),
        Objects.requireNonNullElse(artifact.contentType(), "text/plain"),
        null,
        redactionService.redact(content).getBytes(StandardCharsets.UTF_8));
    artifactCount.incrementAndGet();
  }

  @Override
  public String getName() {
    return "Allure";
  }

  @Override
  public int reportedSteps() {
    return stepCount.get();
  }

  @Override
  public int reportedArtifacts() {
    return artifactCount.get();
  }

  private String requireActiveTest() {
    return requireState().testId;
  }

  private InvocationState requireState() {
    InvocationState state = current.get();
    if (state == null) throw new IllegalStateException("No test is active on this thread");
    return state;
  }

  private static String displayName(TafTest test) {
    if (test.name() != null && !test.name().isBlank()) {
      return test.name();
    }
    return Objects.requireNonNullElse(test.className(), "Unnamed test");
  }

  private static Status toStatus(String status) {
    if (status == null) {
      return null;
    }
    return switch (status.toLowerCase(Locale.ROOT)) {
      case "passed", "success", "successful" -> Status.PASSED;
      case "failed", "failure" -> Status.FAILED;
      case "broken", "error" -> Status.BROKEN;
      case "skipped", "disabled" -> Status.SKIPPED;
      default -> null;
    };
  }

  private static final class InvocationState {
    private final String testId;
    private final ConcurrentHashMap<String, String> stepIds = new ConcurrentHashMap<>();
    private final java.util.Set<String> completedEvents = ConcurrentHashMap.newKeySet();

    private InvocationState(String testId) {
      this.testId = testId;
    }
  }
}
