package com.codinglair.taf.observability;

import java.time.Duration;
import java.util.Objects;

/** Defensive query and response limits enforced independently of provider behavior. */
public record ObservabilityLimits(
    Duration maximumWindow, int maximumRecords, int maximumPayloadChars) {
  public static final ObservabilityLimits DEFAULTS =
      new ObservabilityLimits(Duration.ofMinutes(15), 1_000, 4_096);

  public ObservabilityLimits {
    Objects.requireNonNull(maximumWindow, "maximumWindow");
    if (maximumWindow.isZero() || maximumWindow.isNegative()) {
      throw new IllegalArgumentException("Maximum telemetry window must be positive");
    }
    if (maximumRecords < 1) {
      throw new IllegalArgumentException("Maximum telemetry records must be positive");
    }
    if (maximumPayloadChars < 1) {
      throw new IllegalArgumentException("Maximum telemetry payload characters must be positive");
    }
  }
}
