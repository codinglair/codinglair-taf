package com.codinglair.taf.runtime.core.preflight;

import java.util.Map;
import java.util.Objects;

/** One sanitized, actionable execution-blocking preflight diagnostic. */
public record PreflightDiagnostic(
    String checkId, String message, String correctiveAction, Map<String, String> context) {
  public PreflightDiagnostic {
    checkId = requireText(checkId, "checkId");
    message = requireText(message, "message");
    correctiveAction = requireText(correctiveAction, "correctiveAction");
    context = Map.copyOf(Objects.requireNonNull(context, "context"));
  }

  public PreflightDiagnostic(String checkId, String message, String correctiveAction) {
    this(checkId, message, correctiveAction, Map.of());
  }

  private static String requireText(String value, String field) {
    String normalized = Objects.requireNonNull(value, field).trim();
    if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
    return normalized;
  }
}
