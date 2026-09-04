package com.codinglair.taf.core.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Configuration value type (RT-001, TODO 5). Tests cover serialization,
 * equality, and structural validity checks.
 */
public class ConfigurationTest {

  @Test
  void testSuccessfulConfigurationCreationAndValidation() {
    // Setup a valid configuration
    ValidationResult successResult =
        new ValidationResult(
            "testField", ValidationResult.ValidationStatus.SUCCESS, "OK", "No action needed.");

    Configuration config = new Configuration("config-123", true, java.util.List.of(successResult));

    assertNotNull(config);
    assertEquals("config-123", config.contextId());
    assertTrue(config.isValidated());
    assertEquals(1, config.validationResults().size());
  }

  @Test
  void testConfigurationWithFailures() {
    // Setup a configuration with a failure
    ValidationResult failureResult =
        new ValidationResult(
            "db_host",
            ValidationResult.ValidationStatus.FAILURE,
            "Host unreachable.",
            "Check network connectivity.");

    Configuration config = new Configuration("config-456", true, java.util.List.of(failureResult));

    assertNotNull(config);
    assertFalse(
        config.isFullyValid(), "Configuration should not be fully valid if any validation fails.");
    assertEquals(1, config.validationResults().size());
  }

  @Test
  void testConfigurationConstructorRejection() {
    // Expect IllegalArgumentException when contextId is blank
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new Configuration("", true, java.util.List.of());
        },
        "Configuration constructor should reject blank contextId.");
  }

  @Test
  void testConfigurationWithMixedValidationStates() {
    // Test a mix of success, failure, and warning
    ValidationResult success =
        new ValidationResult("a", ValidationResult.ValidationStatus.SUCCESS, "ok", "N/A");
    ValidationResult failure =
        new ValidationResult("b", ValidationResult.ValidationStatus.FAILURE, "fail", "fix");
    ValidationResult warning =
        new ValidationResult("c", ValidationResult.ValidationStatus.WARNING, "warn", "info");

    Configuration config =
        new Configuration("mixed-config", true, java.util.List.of(success, failure, warning));

    assertNotNull(config);
    // Only failure status should make it invalid
    assertFalse(
        config.isFullyValid(), "Configuration should be invalid if any result is a FAILURE.");
    assertEquals(3, config.validationResults().size());
  }

  @Test
  void testConfigurationValidationResultConstructorRejection() {
    // Expect IllegalArgumentException when fieldName is blank
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new ValidationResult("", ValidationResult.ValidationStatus.FAILURE, "Error", "Fix");
        },
        "ValidationResult constructor should reject blank fieldName.");
  }

  @Test
  void testValidationResultToStringSecurity() {
    // Ensure the custom toString() implementation redacts sensitive data
    String sensitiveMessage = "Configuration contains secret: key-xyz";
    String sensitiveAction = "Resolve secret issue.";
    ValidationResult result =
        new ValidationResult(
            "secret_field",
            ValidationResult.ValidationStatus.FAILURE,
            sensitiveMessage,
            sensitiveAction);

    String output = result.toString();
    // Check that the sensitive content is replaced with [REDACTED]
    assertTrue(output.contains("[REDACTED]"));
    assertFalse(output.contains("key-xyz"), "Secret should be redacted in toString()");
  }

  @Test
  void testConfigurationSerialization() {
    ValidationResult successResult = ValidationResult.success("testField", "Field is valid");
    Configuration config = new Configuration("config-123", true, java.util.List.of(successResult));

    String toStringOutput = config.toString();
    assertTrue(toStringOutput.contains("Configuration"));
    assertTrue(toStringOutput.contains("config-123"));
    assertTrue(toStringOutput.contains("SUCCESS"));
  }

  @Test
  void testConfigurationEqualityContract() {
    ValidationResult result1 =
        new ValidationResult("field1", ValidationResult.ValidationStatus.SUCCESS, "valid", "ok");
    ValidationResult result2 =
        new ValidationResult("field1", ValidationResult.ValidationStatus.SUCCESS, "valid", "ok");

    Configuration config1 = new Configuration("config-1", true, java.util.List.of(result1));
    Configuration config2 = new Configuration("config-1", true, java.util.List.of(result2));

    // Records should be equal if all field values are equal
    assertEquals(config1, config2);
    assertEquals(config1.hashCode(), config2.hashCode());
  }

  @Test
  void testConfigurationValidationResultsEquality() {
    ValidationResult result1 =
        new ValidationResult("field1", ValidationResult.ValidationStatus.SUCCESS, "valid", "ok");
    ValidationResult result2 =
        new ValidationResult("field1", ValidationResult.ValidationStatus.SUCCESS, "valid", "ok");

    // Two configs with same validation results should be equal
    Configuration config1 = new Configuration("config-1", true, java.util.List.of(result1));
    Configuration config2 = new Configuration("config-1", true, java.util.List.of(result2));

    assertEquals(config1, config2);
  }
}
