package com.codinglair.taf.demo.sauce.validation;

import com.codinglair.taf.runtime.core.reporting.annotation.Validation;

public class StringValidator {
  @Validation("Text matches expected value")
  public void validate(String expected, String actual) {
    if (!expected.equals(actual)) {
      throw new AssertionError("Expected '" + expected + "' but was '" + actual + "'");
    }
  }
}
