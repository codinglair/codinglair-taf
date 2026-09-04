package com.codinglair.taf.runtime.environment;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Aggregate preflight outcome with deterministic worst-status selection. */
public record PreflightResult(EnvironmentStatus status, List<PreflightCheckResult> checks) {
  public PreflightResult {
    Objects.requireNonNull(status, "status");
    checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
  }

  public static PreflightResult from(List<PreflightCheckResult> checks) {
    List<PreflightCheckResult> copy = List.copyOf(Objects.requireNonNull(checks, "checks"));
    EnvironmentStatus aggregate =
        copy.stream()
            .map(PreflightCheckResult::status)
            .max(Comparator.comparingInt(PreflightResult::severity))
            .orElse(EnvironmentStatus.UNKNOWN);
    return new PreflightResult(aggregate, copy);
  }

  private static int severity(EnvironmentStatus status) {
    return switch (status) {
      case READY -> 0;
      case UNKNOWN -> 1;
      case DEGRADED -> 2;
      case UNAVAILABLE -> 3;
      case MISCONFIGURED -> 4;
    };
  }
}
