package com.codinglair.taf.runtime.testng;

import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable evidence for one TestNG invocation attempt in the current execution. */
public record TestNgAttemptResult(
    int attemptNumber,
    Instant completedAt,
    Status status,
    Throwable failure,
    List<TestArtifact> artifacts,
    FailureAnalysis failureAnalysis) {

  public TestNgAttemptResult {
    if (attemptNumber < 1) {
      throw new IllegalArgumentException("attemptNumber must be positive");
    }
    Objects.requireNonNull(completedAt, "completedAt");
    Objects.requireNonNull(status, "status");
    artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
    if ((status == Status.FAILED || status == Status.CONFIGURATION_FAILED) && failureAnalysis == null) {
      throw new IllegalArgumentException("failed attempts require authoritative failure analysis");
    }
  }

  /** Additive source-compatible constructor retained for successful/skipped legacy producers. */
  public TestNgAttemptResult(int attemptNumber, Instant completedAt, Status status, Throwable failure,
      List<TestArtifact> artifacts) {
    this(attemptNumber, completedAt, status, failure, artifacts,
        status == Status.FAILED || status == Status.CONFIGURATION_FAILED || status == Status.SKIPPED && failure != null
            ? FailureAnalysis.classify("testng", status == Status.CONFIGURATION_FAILED ? "configuration" : "test",
                status == Status.CONFIGURATION_FAILED
                    ? com.codinglair.taf.runtime.core.failure.FailureContext.Boundary.AUTOMATION
                    : com.codinglair.taf.runtime.core.failure.FailureContext.Boundary.UNKNOWN, failure)
            : null);
  }

  public enum Status {
    PASSED,
    FAILED,
    CONFIGURATION_FAILED,
    SKIPPED
  }
}
