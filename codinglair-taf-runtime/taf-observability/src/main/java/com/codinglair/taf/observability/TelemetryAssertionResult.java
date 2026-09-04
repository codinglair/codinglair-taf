package com.codinglair.taf.observability;

import java.time.Duration;
import java.util.Objects;

/** Outcome of an eventual telemetry assertion. */
public record TelemetryAssertionResult(
    Status status, int pollCount, Duration elapsed, TelemetryQueryResult lastResult) {
  public enum Status {
    MATCHED,
    TIMED_OUT
  }

  public TelemetryAssertionResult {
    Objects.requireNonNull(status, "status");
    if (pollCount < 1) {
      throw new IllegalArgumentException("Poll count must be positive");
    }
    Objects.requireNonNull(elapsed, "elapsed");
    Objects.requireNonNull(lastResult, "lastResult");
  }

  public boolean matched() {
    return status == Status.MATCHED;
  }
}
