package com.codinglair.taf.observability;

/** Provider SPI implemented by observability backends. Implementations must honor query bounds. */
public interface ObservabilityProvider {
  String name();

  TelemetryType type();

  TelemetryQueryResult query(TelemetryQuery query);
}
