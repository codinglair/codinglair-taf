package com.codinglair.taf.runtime.core.reporting.abstraction;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** One immutable, correlated transition in a hierarchical neutral report. */
public record ReportEvent(
    String id,
    String parentId,
    String correlationId,
    ReportLevel level,
    Phase phase,
    String name,
    String status,
    String description,
    Map<String, String> context,
    Instant occurredAt) {

  public ReportEvent {
    id = requireText(id, "id");
    correlationId = requireText(correlationId, "correlationId");
    level = Objects.requireNonNull(level, "level");
    phase = Objects.requireNonNull(phase, "phase");
    name = requireText(name, "name");
    context = Map.copyOf(Objects.requireNonNull(context, "context"));
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  public enum Phase {
    STARTED,
    FINISHED
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(field + " must not be blank");
    return value;
  }
}
