package com.codinglair.taf.conformance;

import java.nio.file.Path;
import java.util.Objects;

/** One actionable consumer-project conformance failure. */
public record ConformanceViolation(String rule, Path location, String message, String correction) {
  public ConformanceViolation {
    rule = require(rule, "rule");
    location = Objects.requireNonNull(location, "location").normalize();
    message = require(message, "message");
    correction = require(correction, "correction");
  }

  private static String require(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
