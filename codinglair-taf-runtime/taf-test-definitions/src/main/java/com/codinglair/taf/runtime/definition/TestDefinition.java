package com.codinglair.taf.runtime.definition;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable persistent definition; actual outputs and execution metadata deliberately live
 * elsewhere.
 */
public record TestDefinition<I, E>(
    String caseId,
    long version,
    DefinitionState state,
    I input,
    E expectedOutput,
    Map<String, SecretReference> secretReferences) {
  public TestDefinition {
    caseId = requireText(caseId, "caseId");
    if (version < 1) throw new IllegalArgumentException("version must be positive");
    state = Objects.requireNonNull(state, "state");
    input = Objects.requireNonNull(input, "input");
    expectedOutput = Objects.requireNonNull(expectedOutput, "expectedOutput");
    secretReferences = Map.copyOf(Objects.requireNonNull(secretReferences, "secretReferences"));
  }

  private static String requireText(String value, String name) {
    String normalized = Objects.requireNonNull(value, name).trim();
    if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return normalized;
  }
}
