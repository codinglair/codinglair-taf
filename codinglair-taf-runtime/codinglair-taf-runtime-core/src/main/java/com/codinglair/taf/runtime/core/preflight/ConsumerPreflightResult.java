package com.codinglair.taf.runtime.core.preflight;

import java.util.List;
import java.util.Objects;

/** Immutable result of one Runtime-wide consumer preflight inspection. */
public record ConsumerPreflightResult(List<PreflightDiagnostic> diagnostics) {
  public ConsumerPreflightResult {
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }

  public boolean passed() {
    return diagnostics.isEmpty();
  }
}
