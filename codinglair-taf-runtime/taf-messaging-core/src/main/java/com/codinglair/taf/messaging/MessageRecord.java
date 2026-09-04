package com.codinglair.taf.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** A message plus sanitized evidence metadata retained by an adapter. */
public record MessageRecord(
    MessageEnvelope envelope, Instant observedAt, Map<String, Object> nativeMetadata) {
  public MessageRecord {
    envelope = Objects.requireNonNull(envelope, "envelope must not be null");
    observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
    nativeMetadata =
        Map.copyOf(Objects.requireNonNull(nativeMetadata, "nativeMetadata must not be null"));
  }
}
