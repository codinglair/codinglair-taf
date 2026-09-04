package com.codinglair.taf.runtime.core.migration;

import java.util.List;
import java.util.Objects;

/** Sanitized validation result; diagnostics contain no physical connection identity. */
public record MigrationValidationResult(boolean valid, List<String> diagnostics) {
  public MigrationValidationResult {
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    if (valid && !diagnostics.isEmpty())
      throw new IllegalArgumentException("A valid result cannot contain diagnostics");
  }

  public static MigrationValidationResult passed() {
    return new MigrationValidationResult(true, List.of());
  }

  public static MigrationValidationResult failed(String diagnostic) {
    return new MigrationValidationResult(false, List.of(Objects.requireNonNull(diagnostic)));
  }
}
