package com.codinglair.taf.observability;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Vendor-neutral telemetry item returned by a provider. */
public record TelemetryRecord(Instant timestamp, Map<String, String> attributes, String payload) {
  public TelemetryRecord {
    Objects.requireNonNull(timestamp, "timestamp");
    attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    payload = Objects.requireNonNull(payload, "payload");
  }
}
