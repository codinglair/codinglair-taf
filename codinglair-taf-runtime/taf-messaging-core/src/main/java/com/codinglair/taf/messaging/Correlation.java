package com.codinglair.taf.messaging;

import java.util.Objects;

/** A technology-neutral correlation name and value. */
public record Correlation(String name, String value) {
  public Correlation {
    name = requireText(name, "name");
    value = requireText(value, "value");
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    return value;
  }
}
