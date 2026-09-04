package com.codinglair.taf.runtime.definition;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Transient actual output and metadata, deliberately separate from persistent definitions. */
public record TestExecutionData<A>(
    String caseId, A actualOutput, Instant observedAt, Map<String, String> metadata) {
  public TestExecutionData {
    caseId = Objects.requireNonNull(caseId, "caseId");
    actualOutput = Objects.requireNonNull(actualOutput, "actualOutput");
    observedAt = Objects.requireNonNull(observedAt, "observedAt");
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
  }
}
