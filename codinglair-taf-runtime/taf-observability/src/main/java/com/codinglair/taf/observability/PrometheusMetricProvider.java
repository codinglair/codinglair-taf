package com.codinglair.taf.observability;

/** Adapter boundary for Prometheus-compatible metric query implementations. */
public interface PrometheusMetricProvider extends ObservabilityProvider {
  @Override
  default TelemetryType type() {
    return TelemetryType.METRIC;
  }
}
