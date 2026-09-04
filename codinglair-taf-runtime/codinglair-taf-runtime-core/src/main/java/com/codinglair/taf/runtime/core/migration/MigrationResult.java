package com.codinglair.taf.runtime.core.migration;

import java.util.List;
import java.util.Objects;

/** Result of applying the configured policy to one logical target. */
public record MigrationResult(
    MigrationOutcome outcome,
    MigrationValidationResult validation,
    List<MigrationEvidence> evidence) {
  public MigrationResult {
    outcome = Objects.requireNonNull(outcome, "outcome");
    validation = Objects.requireNonNull(validation, "validation");
    evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
  }
}
