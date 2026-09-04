package com.codinglair.taf.mcp.jobs;

import java.time.Instant;
import java.util.Objects;

/** Sanitized bounded progress event persisted with a job. */
public record JobEvent(long sequence, Instant occurredAt, String kind, String message) {
  public JobEvent {
    Objects.requireNonNull(occurredAt, "occurredAt");
    kind = bounded(kind, "kind", 64);
    message = bounded(message, "message", 1024);
    if (sequence < 1) {
      throw new IllegalArgumentException("Event sequence must be positive");
    }
  }

  private static String bounded(String value, String name, int maximum) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()
        || value.length() > maximum
        || value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(
          name + " must be nonblank, bounded, and free of control characters");
    }
    return value;
  }
}
