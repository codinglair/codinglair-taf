package com.codinglair.taf.observability;

/** Adapter boundary for Loki-compatible log query implementations. */
public interface LokiLogProvider extends ObservabilityProvider {
  @Override
  default TelemetryType type() {
    return TelemetryType.LOG;
  }
}
