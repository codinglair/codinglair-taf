package com.codinglair.taf.observability;

import java.util.Objects;

/** Signals an observability environment outage, distinct from a successful empty query. */
public final class ProviderUnavailableException extends RuntimeException {
  private final String provider;
  private final TelemetryType telemetryType;

  public ProviderUnavailableException(
      String provider, TelemetryType telemetryType, String correctiveAction, Throwable cause) {
    super(
        "Observability provider '"
            + provider
            + "' is unavailable for "
            + telemetryType
            + "; "
            + correctiveAction,
        cause);
    this.provider = Objects.requireNonNull(provider, "provider");
    this.telemetryType = Objects.requireNonNull(telemetryType, "telemetryType");
  }

  public String provider() {
    return provider;
  }

  public TelemetryType telemetryType() {
    return telemetryType;
  }
}
