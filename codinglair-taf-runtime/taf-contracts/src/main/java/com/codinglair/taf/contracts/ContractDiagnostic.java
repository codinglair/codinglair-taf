package com.codinglair.taf.contracts;

import java.util.Objects;

/** Structured, actionable validation or provider diagnostic. */
public record ContractDiagnostic(
    Severity severity, String code, String location, String message, String correctiveAction) {
  public enum Severity {
    ERROR,
    WARNING
  }

  public ContractDiagnostic {
    severity = Objects.requireNonNull(severity, "severity must not be null");
    code = requireText(code, "code");
    location = requireText(location, "location");
    message = requireText(message, "message");
    correctiveAction = requireText(correctiveAction, "correctiveAction");
  }

  public static ContractDiagnostic error(
      String code, String location, String message, String correctiveAction) {
    return new ContractDiagnostic(Severity.ERROR, code, location, message, correctiveAction);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
