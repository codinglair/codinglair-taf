package com.codinglair.taf.observability;

import com.codinglair.taf.runtime.core.context.CorrelationContext;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, time- and cardinality-bounded telemetry query. */
public record TelemetryQuery(
    TelemetryType type,
    String expression,
    Instant from,
    Instant to,
    int limit,
    Map<String, String> correlation) {

  public TelemetryQuery {
    Objects.requireNonNull(type, "type");
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("Telemetry expression must not be blank");
    }
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("Telemetry query start must be before end");
    }
    if (limit < 1) {
      throw new IllegalArgumentException("Telemetry query limit must be positive");
    }
    correlation = Map.copyOf(Objects.requireNonNull(correlation, "correlation"));
  }

  public static TelemetryQuery logs(String expression, Instant from, Instant to, int limit) {
    return new TelemetryQuery(TelemetryType.LOG, expression, from, to, limit, Map.of());
  }

  public static TelemetryQuery metrics(String expression, Instant from, Instant to, int limit) {
    return new TelemetryQuery(TelemetryType.METRIC, expression, from, to, limit, Map.of());
  }

  public static TelemetryQuery traces(String expression, Instant from, Instant to, int limit) {
    return new TelemetryQuery(TelemetryType.TRACE, expression, from, to, limit, Map.of());
  }

  /** Adds the existing session correlation identifiers without copying arbitrary metadata. */
  public TelemetryQuery correlatedBy(CorrelationContext context) {
    Objects.requireNonNull(context, "context");
    Map<String, String> identifiers = new LinkedHashMap<>();
    identifiers.put("traceId", context.getTraceId());
    identifiers.put("spanId", context.getSpanId());
    if (context.getSessionId() != null) {
      identifiers.put("sessionId", context.getSessionId());
    }
    return new TelemetryQuery(type, expression, from, to, limit, identifiers);
  }

  public Duration window() {
    return Duration.between(from, to);
  }
}
