package com.codinglair.taf.runtime.core.lifecycle;

import java.util.Objects;

/** Technology-neutral identity and ownership information for one test invocation. */
public record InvocationDescriptor(String id, String displayName, String owner) {
  public InvocationDescriptor {
    id = requireText(id, "id");
    displayName = requireText(displayName, "displayName");
    owner = requireText(owner, "owner");
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
