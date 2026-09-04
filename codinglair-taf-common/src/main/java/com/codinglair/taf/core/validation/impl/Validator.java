package com.codinglair.taf.core.validation.impl;

import com.codinglair.taf.core.validation.ValidationResult;
import com.codinglair.taf.core.validation.ValidationResult.ValidationStatus;

/** Generic validator implementation for common validation patterns. */
public class Validator {

  /** Validates that a value is not null. */
  public static <T> ValidationResult notNull(String fieldName, T value) {
    if (value == null) {
      return new ValidationResult(
          fieldName,
          ValidationStatus.FAILURE,
          "Field '" + fieldName + "' must not be null",
          "Provide a non-null value for '" + fieldName + "'.");
    }
    return ValidationResult.success(fieldName, "Field '" + fieldName + "' is present");
  }

  /** Validates that a value is not empty. */
  public static <T> ValidationResult notEmpty(String fieldName, T value) {
    if (value == null || value.equals("")) {
      return new ValidationResult(
          fieldName,
          ValidationStatus.FAILURE,
          "Field '" + fieldName + "' must not be empty",
          "Provide a non-empty value for '" + fieldName + "'.");
    }
    return ValidationResult.success(
        fieldName, "Field '" + fieldName + "' is present and non-empty");
  }

  /** Validates that a string is not blank. */
  public static ValidationResult notBlank(String fieldName, String value) {
    if (value == null || value.isBlank()) {
      return new ValidationResult(
          fieldName,
          ValidationStatus.FAILURE,
          "Field '" + fieldName + "' must not be blank",
          "Provide a non-blank value for '" + fieldName + "'.");
    }
    return ValidationResult.success(
        fieldName, "Field '" + fieldName + "' is present and not blank");
  }

  /** Validates that a value matches an expected value. */
  public static <T> ValidationResult equals(String fieldName, T expected, T actual) {
    if (expected == null && actual == null) {
      return ValidationResult.success(fieldName, "Both fields are null");
    }
    if (expected == null || actual == null) {
      return new ValidationResult(
          fieldName,
          ValidationStatus.FAILURE,
          "Field '" + fieldName + "' does not match expected value",
          "Ensure the value matches the expected value.");
    }
    if (!expected.equals(actual)) {
      return new ValidationResult(
          fieldName,
          ValidationStatus.FAILURE,
          "Field '" + fieldName + "' does not match expected value",
          "The actual value does not match the expected value.");
    }
    return ValidationResult.success(fieldName, "Field '" + fieldName + "' matches expected value");
  }

  /** Validates that a value is within a numeric range. */
  public static ValidationResult range(String fieldName, Number value, Number min, Number max) {
    if (value == null) {
      return new ValidationResult(
          fieldName,
          ValidationStatus.FAILURE,
          "Field '" + fieldName + "' must be a number",
          "Provide a numeric value for '" + fieldName + "'.");
    }
    int cmpMin = value.intValue() - min.intValue();
    int cmpMax = value.intValue() - max.intValue();
    if (cmpMin < 0 || cmpMax > 0) {
      return new ValidationResult(
          fieldName,
          ValidationStatus.FAILURE,
          "Field '" + fieldName + "' is outside the allowed range [" + min + ", " + max + "]",
          "Ensure the value is between " + min + " and " + max + ".");
    }
    return ValidationResult.success(
        fieldName, "Field '" + fieldName + "' is within the allowed range");
  }
}
