package com.codinglair.taf.observability;

import java.util.List;
import java.util.Objects;

/** Safe, bounded result; an empty result means telemetry was available but no data matched. */
public record TelemetryQueryResult(
    TelemetryType type,
    List<TelemetryRecord> records,
    long matchedCount,
    boolean truncated,
    String summary) {
  public TelemetryQueryResult {
    Objects.requireNonNull(type, "type");
    records = List.copyOf(Objects.requireNonNull(records, "records"));
    if (matchedCount < records.size()) {
      throw new IllegalArgumentException("Matched count cannot be smaller than returned records");
    }
    summary = Objects.requireNonNull(summary, "summary");
  }

  public boolean hasData() {
    return !records.isEmpty();
  }
}
