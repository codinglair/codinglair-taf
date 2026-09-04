package com.codinglair.taf.runtime.definition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable definition version plus repository-neutral correlation and payload metadata. */
public record VersionedTestDefinition<I, E>(
    TestDefinition<I, E> definition,
    Map<String, String> correlations,
    List<PayloadReference> payloadReferences) {
  public VersionedTestDefinition {
    definition = Objects.requireNonNull(definition, "definition");
    correlations = Map.copyOf(Objects.requireNonNull(correlations, "correlations"));
    payloadReferences = List.copyOf(Objects.requireNonNull(payloadReferences, "payloadReferences"));
  }

  public String caseId() {
    return definition.caseId();
  }

  public long version() {
    return definition.version();
  }

  public DefinitionState state() {
    return definition.state();
  }
}
