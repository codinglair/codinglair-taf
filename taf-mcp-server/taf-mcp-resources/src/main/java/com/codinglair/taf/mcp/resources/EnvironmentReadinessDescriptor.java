package com.codinglair.taf.mcp.resources;

import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import com.codinglair.taf.runtime.environment.PreflightResult;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Sanitized preflight summary. Diagnostic messages and provider metadata never cross this boundary.
 */
public record EnvironmentReadinessDescriptor(
    String environment, EnvironmentStatus status, List<CheckSummary> checks) {
  public record CheckSummary(String id, String type, EnvironmentStatus status) {}

  public EnvironmentReadinessDescriptor {
    if (environment == null || environment.isBlank()) {
      throw new IllegalArgumentException("environment is required");
    }
    Objects.requireNonNull(status, "status");
    checks = List.copyOf(checks);
  }

  public static EnvironmentReadinessDescriptor from(String environment, PreflightResult result) {
    Objects.requireNonNull(result, "result");
    var summaries =
        result.checks().stream()
            .map(check -> new CheckSummary(check.checkId(), check.type().name(), check.status()))
            .sorted(Comparator.comparing(CheckSummary::id))
            .toList();
    return new EnvironmentReadinessDescriptor(environment, result.status(), summaries);
  }
}
