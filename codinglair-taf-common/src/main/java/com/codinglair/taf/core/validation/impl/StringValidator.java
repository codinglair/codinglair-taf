package com.codinglair.taf.core.validation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.validation.ValidationResult;
import com.codinglair.taf.core.validation.abstraction.Validator;

public class StringValidator implements Validator<String> {
  private boolean ignoreCaseFlag = false;

  public StringValidator setIgnoreCase(boolean ignoreCaseFlag) {
    this.ignoreCaseFlag = ignoreCaseFlag;
    return this;
  }

  @Override
  @TafStep("Validate that '{1}' matches expected value: '{0}'")
  public void validate(String expected, String actual) {
    if (expected == null && actual == null) {
      throw new AssertionError(
          String.format("Null mismatch! Expected: [%s], Actual: [%s]", expected, actual));
    }
    ;
    if (ignoreCaseFlag) {
      assertThat(actual)
          .as("Comparing string values ignoring case")
          .isEqualToIgnoringCase(expected);
    } else {
      assertThat(actual).as("Comparing string values").isEqualTo(expected);
    }
  }

  @Override
  public ValidationResult validateAndGetResult(String expected, String actual) {
    if (expected == null && actual == null) {
      return ValidationResult.failure("Null mismatch! Expected: null, Actual: null");
    }
    if (ignoreCaseFlag) {
      if (!expected.equalsIgnoreCase(actual)) {
        return ValidationResult.failure(
            "String values not equal (ignoring case). Expected: ["
                + expected
                + "], Actual: ["
                + actual
                + "]");
      }
    } else {
      if (!expected.equals(actual)) {
        return ValidationResult.failure(
            "String values not equal. Expected: [" + expected + "], Actual: [" + actual + "]");
      }
    }
    return ValidationResult.success();
  }
}
