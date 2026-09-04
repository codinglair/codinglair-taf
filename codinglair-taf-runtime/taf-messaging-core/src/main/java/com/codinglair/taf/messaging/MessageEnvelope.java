package com.codinglair.taf.messaging;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Technology-neutral message content. Provider-specific data belongs in {@link MessageRecord}. */
public record MessageEnvelope(
    byte[] payload, Map<String, String> headers, Optional<Correlation> correlation) {
  public MessageEnvelope {
    payload = Objects.requireNonNull(payload, "payload must not be null").clone();
    headers = Map.copyOf(Objects.requireNonNull(headers, "headers must not be null"));
    correlation = Objects.requireNonNull(correlation, "correlation must not be null");
  }

  public MessageEnvelope(byte[] payload) {
    this(payload, Map.of(), Optional.empty());
  }

  @Override
  public byte[] payload() {
    return payload.clone();
  }
}
