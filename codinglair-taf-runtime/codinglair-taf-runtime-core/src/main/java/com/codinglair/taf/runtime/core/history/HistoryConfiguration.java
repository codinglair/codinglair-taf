package com.codinglair.taf.runtime.core.history;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Validated local/CI history policy. */
public record HistoryConfiguration(
    boolean enabled,
    Path root,
    int maximumRecords,
    Duration maximumAge,
    long maximumBytes,
    UnavailabilityPolicy unavailabilityPolicy,
    CorruptionPolicy corruptionPolicy) {
  public HistoryConfiguration {
    root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    if (maximumRecords < 1 || maximumRecords > 100_000) throw new IllegalArgumentException("maximumRecords must be 1-100000");
    maximumAge = Objects.requireNonNull(maximumAge, "maximumAge");
    if (maximumAge.isNegative() || maximumAge.isZero()) throw new IllegalArgumentException("maximumAge must be positive");
    if (maximumBytes < 1024) throw new IllegalArgumentException("maximumBytes must be at least 1024");
    Objects.requireNonNull(unavailabilityPolicy, "unavailabilityPolicy");
    Objects.requireNonNull(corruptionPolicy, "corruptionPolicy");
  }

  public enum UnavailabilityPolicy { CONTINUE, REQUIRE_HISTORY }
  public enum CorruptionPolicy { REPORT, QUARANTINE }
}
