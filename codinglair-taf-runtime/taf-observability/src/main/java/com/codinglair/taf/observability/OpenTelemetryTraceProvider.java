package com.codinglair.taf.observability;

/** Adapter boundary for OpenTelemetry-compatible trace query implementations. */
public interface OpenTelemetryTraceProvider extends ObservabilityProvider {
  @Override
  default TelemetryType type() {
    return TelemetryType.TRACE;
  }
}
