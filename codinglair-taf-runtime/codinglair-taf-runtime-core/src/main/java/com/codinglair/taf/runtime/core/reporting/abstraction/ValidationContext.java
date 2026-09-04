package com.codinglair.taf.runtime.core.reporting.abstraction;

import java.util.Map;
import java.util.Objects;

/** Sanitized expected/actual context for a validation event. */
public record ValidationContext(String expected, String actual, Map<String, String> metadata) {
  public ValidationContext {
    expected = Objects.requireNonNull(expected, "expected");
    actual = Objects.requireNonNull(actual, "actual");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
