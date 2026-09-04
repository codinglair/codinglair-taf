package com.codinglair.taf.runtime.core.history;

import com.codinglair.taf.core.Error.ErrorType;
import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.failure.FailureClassification;
import com.codinglair.taf.runtime.core.failure.FailureClassificationService;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import com.codinglair.taf.runtime.core.failure.FailureSignature;
import com.codinglair.taf.runtime.core.failure.FailureSignatureService;
import com.codinglair.taf.runtime.core.failure.StabilityStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Completes one immutable attempt by classifying, signing, evaluating, and
 * recording it.
 */
public final class AttemptHistoryService {
  private final FailureClassificationService classifications;
  private final FailureSignatureService signatures;
  private final ExecutionHistoryRepository repository;
  private final HistoryConfiguration configuration;
  private final int minimumSamples;
  private final String defaultProjectId;
  private final String defaultBuildId;
  private final String defaultEnvironmentId;

  public AttemptHistoryService(FailureClassificationService classifications,
      FailureSignatureService signatures, ExecutionHistoryRepository repository,
      HistoryConfiguration configuration, int minimumSamples) {
    this(classifications, signatures, repository, configuration, minimumSamples,
        "default", "unknown", "local");
  }

  public AttemptHistoryService(FailureClassificationService classifications,
      FailureSignatureService signatures, ExecutionHistoryRepository repository,
      HistoryConfiguration configuration, int minimumSamples, String defaultProjectId,
      String defaultBuildId, String defaultEnvironmentId) {
    this.classifications = Objects.requireNonNull(classifications);
    this.signatures = Objects.requireNonNull(signatures);
    this.repository = Objects.requireNonNull(repository);
    this.configuration = Objects.requireNonNull(configuration);
    if (minimumSamples < 2)
      throw new IllegalArgumentException("minimumSamples must be at least two");
    this.minimumSamples = minimumSamples;
    this.defaultProjectId = identity(defaultProjectId, "defaultProjectId");
    this.defaultBuildId = identity(defaultBuildId, "defaultBuildId");
    this.defaultEnvironmentId = identity(defaultEnvironmentId, "defaultEnvironmentId");
  }

  public AttemptCompletion complete(AttemptDescriptor attempt) {
    attempt = attempt.withDefaults(defaultProjectId, defaultBuildId, defaultEnvironmentId);
    FailureClassification classification = null;
    FailureSignature signature = null;
    if (attempt.outcome() == ExecutionAttemptSummary.Outcome.FAILED
        || attempt.outcome() == ExecutionAttemptSummary.Outcome.INCONCLUSIVE) {
      FailureContext context = new FailureContext(attempt.capability(), attempt.phase(), attempt.boundary(),
          attempt.failure(), attempt.explicitClassifications());
      classification = classifications.classify(context);
      signature = signatures.sign(context);
    }
    HistoryResult prior = repository.findByTest(attempt.projectId(), attempt.testId(),
        configuration.maximumRecords(), configuration.maximumAge());
    StabilityStatus stability = StabilityStatus.INSUFFICIENT_HISTORY;
    if (prior.status() == HistoryResult.Status.SUCCESS) {
      var candidates = new ArrayList<>(prior.records());
      candidates.add(summary(attempt, classification, signature, stability));
      stability = new HistoricalStabilityEvaluator().evaluate(candidates, minimumSamples,
          new HistoricalStabilityEvaluator.CompatibilityScope(Set.of(attempt.buildId()),
              Set.of(attempt.environmentId())));
    }
    ExecutionAttemptSummary summary = summary(attempt, classification, signature, stability);
    HistoryResult recorded = repository.record(summary);
    HistoryResult.Status effective = prior.status() == HistoryResult.Status.SUCCESS ? recorded.status()
        : prior.status();
    FailureAnalysis analysis = classification == null ? null
        : new FailureAnalysis(classification, signature, stability, historyStatus(effective));
    return new AttemptCompletion(summary, analysis, effective);
  }

  private static ExecutionAttemptSummary summary(AttemptDescriptor a, FailureClassification c,
      FailureSignature s, StabilityStatus stability) {
    return new ExecutionAttemptSummary(1, a.projectId(), a.testId(), a.executionId(), a.attemptId(),
        a.completedAt(), a.outcome(), c, stability, s, a.duration(), a.buildId(), a.environmentId());
  }

  private static FailureAnalysis.HistoryStatus historyStatus(HistoryResult.Status status) {
    return switch (status) {
      case SUCCESS -> FailureAnalysis.HistoryStatus.SUCCESS;
      case DISABLED -> FailureAnalysis.HistoryStatus.DISABLED;
      case UNAVAILABLE -> FailureAnalysis.HistoryStatus.UNAVAILABLE;
      case CORRUPT -> FailureAnalysis.HistoryStatus.CORRUPT;
    };
  }

  public record AttemptDescriptor(String projectId, String testId, String executionId, String attemptId,
      Instant completedAt, ExecutionAttemptSummary.Outcome outcome, Duration duration, String buildId,
      String environmentId, String capability, String phase, FailureContext.Boundary boundary,
      Throwable failure, List<ErrorType> explicitClassifications) {
    public AttemptDescriptor {
      Objects.requireNonNull(completedAt);
      Objects.requireNonNull(outcome);
      Objects.requireNonNull(duration);
      explicitClassifications = List.copyOf(Objects.requireNonNullElse(explicitClassifications, List.of()));
    }

    AttemptDescriptor withDefaults(String project, String build, String environment) {
      return new AttemptDescriptor(projectId == null || projectId.isBlank() ? project : projectId,
          testId, executionId, attemptId, completedAt, outcome, duration,
          buildId == null || buildId.isBlank() ? build : buildId,
          environmentId == null || environmentId.isBlank() ? environment : environmentId,
          capability, phase, boundary, failure, explicitClassifications);
    }
  }

  public record AttemptCompletion(ExecutionAttemptSummary summary, FailureAnalysis analysis,
      HistoryResult.Status historyStatus) {
  }

  private static String identity(String value, String name) {
    if (value == null || !value.matches("[A-Za-z0-9._-]{1,160}"))
      throw new IllegalArgumentException(name + " is unsafe");
    return value;
  }
}
