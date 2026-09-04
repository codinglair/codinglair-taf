package com.codinglair.taf.core.validation;

/**
 * Represents the result of a validation check, adhering to the structured failure taxonomy required
 * by RT-001 (TODO 3). This is a dependency-light, immutable value object contract.
 *
 * <p>Adheres to RT-001: Structured error taxonomy with corrective action.
 */
public record ValidationResult(
    String fieldName, ValidationStatus status, String message, String correctiveActionHint) {

  public enum ValidationStatus {
    SUCCESS,
    FAILURE,
    WARNING
  }

  /** Constructor validation ensures all necessary fields for a structured failure are present. */
  public ValidationResult {
    if (fieldName == null || fieldName.isBlank()) {
      throw new IllegalArgumentException("Field name must not be null or empty.");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Validation message must not be null or empty.");
    }
  }

  /**
   * Factory method for successful validation results.
   *
   * @param fieldName The field name
   * @param description Description of the successful validation
   * @return A ValidationResult with SUCCESS status
   */
  public static ValidationResult success(String fieldName, String description) {
    return new ValidationResult(fieldName, ValidationStatus.SUCCESS, description, null);
  }

  /**
   * Factory method for successful validation results.
   *
   * @return A ValidationResult with SUCCESS status
   */
  public static ValidationResult success() {
    return new ValidationResult("validation", ValidationStatus.SUCCESS, "Validation passed", null);
  }

  /**
   * Factory method for failed validation results.
   *
   * @param message The failure message
   * @return A ValidationResult with FAILURE status
   */
  public static ValidationResult failure(String message) {
    return new ValidationResult(
        "validation", ValidationStatus.FAILURE, message, "Check input values");
  }

  /**
   * Factory method for failed validation results.
   *
   * @param fieldName The field name
   * @param message The failure message
   * @return A ValidationResult with FAILURE status
   */
  public static ValidationResult failure(String fieldName, String message) {
    return new ValidationResult(fieldName, ValidationStatus.FAILURE, message, "Check input values");
  }

  /**
   * Custom toString implementation to prevent accidental logging or reporting of sensitive
   * information (TODO 4). This adheres to the security mandate of never exposing secrets.
   */
  @Override
  public String toString() {
    String maskedMessage = message.contains("secret") ? "[REDACTED]" : message;
    String maskedAction =
        correctiveActionHint != null && correctiveActionHint.contains("secret")
            ? "[REDACTED]"
            : correctiveActionHint;
    return "ValidationResult(fieldName="
        + fieldName
        + ", status="
        + status
        + ", message="
        + maskedMessage
        + ", correctiveActionHint="
        + maskedAction
        + ")";
  }
}
