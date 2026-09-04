package com.codinglair.taf.core.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for the ValidationResult value type (RT-001, TODO 5). */
public class ValidationResultTest {

  @Test
  void testSuccessfulValidationResult() {
    ValidationResult result =
        new ValidationResult(
            "fieldName",
            ValidationResult.ValidationStatus.SUCCESS,
            "Field is valid.",
            "No action needed.");

    assertNotNull(result);
    assertEquals("fieldName", result.fieldName());
    assertEquals(ValidationResult.ValidationStatus.SUCCESS, result.status());
    assertEquals("Field is valid.", result.message());
    assertEquals("No action needed.", result.correctiveActionHint());
  }

  @Test
  void testFailureValidationResult() {
    ValidationResult result =
        new ValidationResult(
            "fieldName",
            ValidationResult.ValidationStatus.FAILURE,
            "Field is mandatory but missing.",
            "Provide a non-empty value.");

    assertNotNull(result);
    assertEquals(ValidationResult.ValidationStatus.FAILURE, result.status());
    assertEquals("Field is mandatory but missing.", result.message());
    assertEquals("Provide a non-empty value.", result.correctiveActionHint());
  }

  @Test
  void testWarningValidationResult() {
    ValidationResult result =
        new ValidationResult(
            "fieldName",
            ValidationResult.ValidationStatus.WARNING,
            "Field value is deprecated.",
            "Update to new standard.");

    assertNotNull(result);
    assertEquals(ValidationResult.ValidationStatus.WARNING, result.status());
    assertEquals("Field value is deprecated.", result.message());
    assertEquals("Update to new standard.", result.correctiveActionHint());
  }

  @Test
  void testValidationResultConstructorRejection() {
    // Test rejection if fieldName is blank
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new ValidationResult("", ValidationResult.ValidationStatus.SUCCESS, "Test", "Action");
        },
        "ValidationResult should reject empty fieldName.");

    // Test rejection if message is blank
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new ValidationResult(
              "fieldName", ValidationResult.ValidationStatus.SUCCESS, "", "Action");
        },
        "ValidationResult should reject empty message.");
  }

  @Test
  void testValidationResultSerialization() {
    // Use the factory method that takes fieldName and message
    ValidationResult result = ValidationResult.failure("db_host", "Connection failed");

    // Verify toString output contains expected structure
    String toStringOutput = result.toString();
    assertTrue(toStringOutput.contains("ValidationResult"));
    assertTrue(toStringOutput.contains("db_host"));
    assertTrue(toStringOutput.contains("FAILURE"));
    assertTrue(toStringOutput.contains("Connection failed"));
  }

  @Test
  void testValidationResultEqualityContract() {
    ValidationResult result1 =
        new ValidationResult("field1", ValidationResult.ValidationStatus.SUCCESS, "valid", "ok");
    ValidationResult result2 =
        new ValidationResult("field1", ValidationResult.ValidationStatus.SUCCESS, "valid", "ok");

    // Records should be equal if all field values are equal
    assertEquals(result1, result2);
    assertEquals(result1.hashCode(), result2.hashCode());
  }

  @Test
  void testValidationResultRedaction() {
    ValidationResult result =
        new ValidationResult(
            "secret", ValidationResult.ValidationStatus.FAILURE, "secret=123", "fix secret");
    String toString = result.toString();

    // Ensure secrets are redacted in toString
    assertTrue(toString.contains("[REDACTED]"));
    assertFalse(toString.contains("123"));
  }
}
