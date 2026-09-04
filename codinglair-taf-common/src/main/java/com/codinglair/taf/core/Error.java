package com.codinglair.taf.core;

/**
 * Represents a structured error within the TAF Runtime, adhering to RT-001 (TODO 3). This is a
 * dependency-light, immutable value object contract that encapsulates the failure's nature,
 * description, and a suggested corrective action.
 *
 * <p>Adheres to RT-001: Structured error taxonomy with corrective action.
 */
public record Error(ErrorType type, String message, String correctiveAction) {

  public enum ErrorType {
    PRODUCT_DEFECT,
    AUTOMATION_FAILURE,
    ENVIRONMENT_ISSUE,
    TEST_DATA_ISSUE,
    FLAKY,
    REQUIREMENT_AMBIGUITY,
    INCONCLUSIVE,
    OTHER
  }

  /** Constructor validation ensures that the error is properly classified and described. */
  public Error {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Error message must not be null or empty.");
    }
    if (correctiveAction == null || correctiveAction.isBlank() && type != ErrorType.INCONCLUSIVE) {
      // For this initial implementation, we enforce a non-blank corrective action for most types.
      // This aids in driving resolution for product/environment/test-data issues.
      if (type != ErrorType.INCONCLUSIVE) {
        throw new IllegalArgumentException(
            "Corrective action must be specified for this error type.");
      }
    }
  }

  /**
   * Custom toString implementation to prevent accidental logging or reporting of sensitive
   * information (TODO 4). This adheres to the security mandate of never exposing secrets.
   */
  @Override
  public String toString() {
    String maskedMessage = message.contains("secret") ? "[REDACTED]" : message;
    String maskedAction = correctiveAction.contains("secret") ? "[REDACTED]" : correctiveAction;
    return "Error(type="
        + type
        + ", message="
        + maskedMessage
        + ", correctiveAction="
        + maskedAction
        + ")";
  }
}
