package com.codinglair.taf.mcp.tools;

import java.util.Objects;
import java.util.Set;

/** A named, operation-bounded capability required by a coarse-grained job. */
public record RequiredCapability(String capabilityId, String instance, Set<String> operations) {
  public RequiredCapability {
    capabilityId = token(capabilityId, "capabilityId");
    instance = token(instance, "instance");
    operations = operations(operations);
  }

  private static String token(String value, String field) {
    if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
      throw new IllegalArgumentException(field + " is invalid");
    }
    return value;
  }

  private static Set<String> operations(Set<String> values) {
    Objects.requireNonNull(values, "operations");
    if (values.isEmpty()
        || values.stream()
            .anyMatch(value -> value == null || !value.matches("[a-z][a-z0-9-]{0,63}"))) {
      throw new IllegalArgumentException("operations must contain safe operation identifiers");
    }
    return Set.copyOf(values);
  }
}
