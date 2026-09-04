package com.codinglair.taf.runtime.environment;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Sanitized point-in-time environment evidence. */
public record EnvironmentDiagnostic(
    EnvironmentStatus status,
    String summary,
    String correctiveAction,
    Map<String, String> details,
    Instant observedAt) {

  public EnvironmentDiagnostic {
    Objects.requireNonNull(status, "status");
    if (summary == null || summary.isBlank()) {
      throw new IllegalArgumentException("Diagnostic summary must not be blank");
    }
    summary = DiagnosticSanitizer.sanitize(summary);
    correctiveAction = DiagnosticSanitizer.sanitize(correctiveAction);
    details = DiagnosticSanitizer.sanitize(Objects.requireNonNull(details, "details"));
    Objects.requireNonNull(observedAt, "observedAt");
  }
}
