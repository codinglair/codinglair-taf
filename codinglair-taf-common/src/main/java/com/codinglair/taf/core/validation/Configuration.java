package com.codinglair.taf.core.validation;

import java.util.List;

/**
 * A container for a set of validated configuration properties for a specific context. This record
 * represents the structured configuration model required by RT-001 (TODO 2). It is dependency-light
 * and immutable.
 */
public record Configuration(
    String contextId, boolean isValidated, List<ValidationResult> validationResults) {

  /**
   * Validates the configuration structure itself, ensuring consistency (e.g., non-null contextId).
   */
  public Configuration {
    if (contextId == null || contextId.isBlank()) {
      throw new IllegalArgumentException("Context ID must not be null or empty.");
    }
  }

  /** A helper method to determine overall validation status. */
  public boolean isFullyValid() {
    if (!isValidated) {
      return false;
    }
    return validationResults.stream()
        .noneMatch(result -> result.status() != ValidationResult.ValidationStatus.SUCCESS);
  }
}
