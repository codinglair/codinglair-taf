package com.codinglair.taf.runtime.cucumber;

import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import com.codinglair.taf.runtime.core.history.AttemptHistoryService;
import com.codinglair.taf.runtime.core.history.DisabledExecutionHistoryRepository;
import com.codinglair.taf.runtime.core.history.ExecutionAttemptSummary;
import com.codinglair.taf.runtime.core.history.HistoryConfiguration;
import com.codinglair.taf.runtime.core.failure.FailureClassificationService;
import com.codinglair.taf.runtime.core.failure.FailureSignatureService;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EmbedEvent;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;

/** Maps Cucumber events to a business-only report without technical diagnostics. */
public final class CucumberBusinessReportPlugin implements ConcurrentEventListener {
  private final Map<UUID, ScenarioState> states = new ConcurrentHashMap<>();
  private final List<CucumberBusinessResult> results = new CopyOnWriteArrayList<>();
  private final List<CucumberBusinessResultListener> listeners;
  private final RedactionPipeline redaction = new RedactionPipeline();
  private final AttemptHistoryService attemptHistory;

  public CucumberBusinessReportPlugin() {
    this(load(CucumberBusinessResultListener.class), disabledHistory());
  }

  public CucumberBusinessReportPlugin(List<CucumberBusinessResultListener> listeners) {
    this(listeners, disabledHistory());
  }

  public CucumberBusinessReportPlugin(List<CucumberBusinessResultListener> listeners,
      AttemptHistoryService attemptHistory) {
    this.listeners = List.copyOf(listeners);
    this.attemptHistory = java.util.Objects.requireNonNull(attemptHistory);
  }

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestCaseStarted.class, this::caseStarted);
    publisher.registerHandlerFor(TestStepStarted.class, this::stepStarted);
    publisher.registerHandlerFor(TestStepFinished.class, this::stepFinished);
    publisher.registerHandlerFor(EmbedEvent.class, this::embedded);
    publisher.registerHandlerFor(TestCaseFinished.class, this::caseFinished);
  }

  /** Returns an immutable snapshot of completed business results. */
  public List<CucumberBusinessResult> results() {
    return List.copyOf(results);
  }

  private void caseStarted(TestCaseStarted event) {
    states.put(event.getTestCase().getId(), new ScenarioState(event.getTestCase()));
  }

  private void stepStarted(TestStepStarted event) {
    if (event.getTestStep() instanceof PickleStepTestStep step) {
      state(event.getTestCase()).currentStep = step.getStepText();
    }
  }

  private void stepFinished(TestStepFinished event) {
    if (event.getTestStep() instanceof PickleStepTestStep step) {
      ScenarioState state = state(event.getTestCase());
      state.steps.add(
          new BusinessStepResult(
              step.getStep().getKeyword().trim(),
              step.getStepText(),
              event.getResult().getStatus().name()));
      state.currentStep = null;
    }
  }

  private void embedded(EmbedEvent event) {
    ScenarioState state = state(event.getTestCase());
    String content =
        isTextual(event.getMediaType())
            ? redaction.redact(new String(event.getData(), StandardCharsets.UTF_8))
            : "[BINARY CONTENT OMITTED]";
    String name = event.getName() == null ? "scenario-evidence" : event.getName();
    state.artifacts.add(
        TestArtifact.of(
            name,
            "cucumber-attachment",
            content,
            event.getMediaType(),
            state.currentStep,
            TestArtifact.computeHash(content)));
  }

  private static boolean isTextual(String mediaType) {
    String normalized = mediaType.toLowerCase(java.util.Locale.ROOT);
    return normalized.startsWith("text/")
        || normalized.contains("json")
        || normalized.contains("xml")
        || normalized.contains("yaml")
        || normalized.contains("x-www-form-urlencoded");
  }

  private void caseFinished(TestCaseFinished event) {
    ScenarioState state = states.remove(event.getTestCase().getId());
    if (state == null) {
      return;
    }
    var nativeStatus = event.getResult().getStatus();
    var outcome = nativeStatus == io.cucumber.plugin.event.Status.PASSED
        ? ExecutionAttemptSummary.Outcome.PASSED
        : nativeStatus == io.cucumber.plugin.event.Status.FAILED
            ? ExecutionAttemptSummary.Outcome.FAILED : ExecutionAttemptSummary.Outcome.INCONCLUSIVE;
    var completion = attemptHistory.complete(new AttemptHistoryService.AttemptDescriptor(null,
        hash(state.testCase.getUri() + ":" + state.testCase.getLine()), state.testCase.getId().toString(),
        "attempt-1", java.time.Instant.now(), outcome,
        Duration.between(state.startedAt, Instant.now()), null, null, "cucumber",
        "scenario-or-hook", FailureContext.Boundary.UNKNOWN, event.getResult().getError(), List.of()));
    CucumberBusinessResult result =
        new CucumberBusinessResult(
            state.testCase.getId().toString(),
            state.testCase.getName(),
            state.testCase.getUri(),
            state.testCase.getLine(),
            event.getResult().getStatus().name(),
            state.testCase.getTags(),
            state.steps,
            state.artifacts,
            completion.analysis());
    results.add(result);
    listeners.forEach(listener -> listener.onResult(result));
  }

  private ScenarioState state(TestCase testCase) {
    ScenarioState state = states.get(testCase.getId());
    if (state == null) {
      throw new IllegalStateException(
          "No reporting state for Cucumber scenario " + testCase.getId());
    }
    return state;
  }

  private static <T> List<T> load(Class<T> providerType) {
    return ServiceLoader.load(providerType).stream().map(ServiceLoader.Provider::get).toList();
  }

  private static final class ScenarioState {
    private final TestCase testCase;
    private final List<BusinessStepResult> steps = new ArrayList<>();
    private final List<TestArtifact> artifacts = new ArrayList<>();
    private volatile String currentStep;
    private final Instant startedAt = Instant.now();

    private ScenarioState(TestCase testCase) {
      this.testCase = testCase;
    }
  }

  private static String hash(String value) {
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
