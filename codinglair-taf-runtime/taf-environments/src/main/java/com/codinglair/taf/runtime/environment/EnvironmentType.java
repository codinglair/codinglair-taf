package com.codinglair.taf.runtime.environment;

/** A user-extensible environment resource type. */
public record EnvironmentType(String name) {
  public EnvironmentType {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Environment type name must not be blank");
    }
    name = name.trim();
  }
}
