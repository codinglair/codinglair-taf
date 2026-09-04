package com.codinglair.taf.runtime.environment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes suite-wide checks plus checks for capabilities requested by the suite. */
public final class PreflightRunner {
  public PreflightResult run(EnvironmentRequest request, List<? extends PreflightCheck> checks) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(checks, "checks");
    List<PreflightCheckResult> results =
        checks.stream()
            .filter(
                check ->
                    check.capability().isEmpty()
                        || request.capabilities().contains(check.capability().orElseThrow()))
            .map(check -> executeSafely(check, request))
            .toList();
    return PreflightResult.from(results);
  }

  private PreflightCheckResult executeSafely(PreflightCheck check, EnvironmentRequest request) {
    try {
      return Objects.requireNonNull(check.execute(request), "Preflight check returned null");
    } catch (RuntimeException failure) {
      return new PreflightCheckResult(
          check.id(),
          check.type(),
          check.capability(),
          EnvironmentStatus.UNKNOWN,
          "Preflight check failed: " + DiagnosticSanitizer.sanitize(failure.getMessage()),
          "Inspect the provider configuration and sanitized diagnostics",
          Map.of(),
          Instant.now());
    }
  }
}
