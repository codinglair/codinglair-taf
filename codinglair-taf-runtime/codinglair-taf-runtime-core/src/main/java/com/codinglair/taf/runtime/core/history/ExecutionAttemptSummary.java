package com.codinglair.taf.runtime.core.history;

import com.codinglair.taf.runtime.core.failure.FailureClassification;
import com.codinglair.taf.runtime.core.failure.FailureSignature;
import com.codinglair.taf.runtime.core.failure.StabilityStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable bounded metadata for one attempt; raw failure/evidence content is deliberately absent. */
public record ExecutionAttemptSummary(
    int schemaVersion,
    String projectId,
    String testId,
    String executionId,
    String attemptId,
    Instant completedAt,
    Outcome outcome,
    FailureClassification classification,
    StabilityStatus stability,
    FailureSignature signature,
    Duration duration,
    String buildId,
    String environmentId) {
  public ExecutionAttemptSummary {
    if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported history schema version");
    projectId = identity(projectId, "projectId");
    testId = identity(testId, "testId");
    executionId = identity(executionId, "executionId");
    attemptId = identity(attemptId, "attemptId");
    Objects.requireNonNull(completedAt, "completedAt");
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(stability, "stability");
    duration = Objects.requireNonNull(duration, "duration");
    if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
    buildId = optionalIdentity(buildId);
    environmentId = optionalIdentity(environmentId);
    if ((outcome == Outcome.FAILED || outcome == Outcome.INCONCLUSIVE) && classification == null) {
      throw new IllegalArgumentException("failed/inconclusive attempts require classification");
    }
  }

  private static String identity(String value, String name) {
    if (value == null || !value.matches("[A-Za-z0-9._-]{1,160}")) {
      throw new IllegalArgumentException(name + " is not a safe stable identity");
    }
    return value;
  }

  private static String optionalIdentity(String value) {
    return value == null || value.isBlank() ? "unknown" : identity(value, "compatibility identity");
  }

  public enum Outcome { PASSED, FAILED, INCONCLUSIVE, SKIPPED, ABORTED }
}
