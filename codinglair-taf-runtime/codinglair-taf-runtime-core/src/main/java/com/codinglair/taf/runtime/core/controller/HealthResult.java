package com.codinglair.taf.runtime.core.controller;

import java.util.Map;

/** Secret-safe health result. */
public record HealthResult(Status status, String summary, Map<String, String> diagnostics) {
  public enum Status {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
    UNKNOWN
  }

  public HealthResult {
    diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
  }

  public static HealthResult unknown(String summary) {
    return new HealthResult(Status.UNKNOWN, summary, Map.of());
  }
}
