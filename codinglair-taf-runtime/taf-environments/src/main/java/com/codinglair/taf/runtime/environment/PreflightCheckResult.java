package com.codinglair.taf.runtime.environment;

import com.codinglair.taf.core.Capability;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Sanitized result for one preflight check. */
public record PreflightCheckResult(
    String checkId,
    PreflightCheckType type,
    Optional<Capability> capability,
    EnvironmentStatus status,
    String message,
    String correctiveAction,
    Map<String, String> diagnostics,
    Instant checkedAt) {

  public PreflightCheckResult {
    if (checkId == null || checkId.isBlank()) {
      throw new IllegalArgumentException("Check id must not be blank");
    }
    capability = Objects.requireNonNull(capability, "capability");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(status, "status");
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Check message must not be blank");
    }
    message = DiagnosticSanitizer.sanitize(message);
    correctiveAction = DiagnosticSanitizer.sanitize(correctiveAction);
    diagnostics = DiagnosticSanitizer.sanitize(Objects.requireNonNull(diagnostics, "diagnostics"));
    Objects.requireNonNull(checkedAt, "checkedAt");
  }
}
