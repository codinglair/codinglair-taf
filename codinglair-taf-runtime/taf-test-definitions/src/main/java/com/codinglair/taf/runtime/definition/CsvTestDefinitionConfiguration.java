package com.codinglair.taf.runtime.definition;

import java.util.Map;
import java.util.Objects;

/** Complete typed configuration for paired CSV input and expected-output sources. */
public record CsvTestDefinitionConfiguration(
    DefinitionResourceLocation inputs,
    DefinitionResourceLocation expectedOutputs,
    String caseIdColumn,
    Map<String, SecretFieldDefinition> inputSecretFields,
    Map<String, SecretFieldDefinition> expectedOutputSecretFields) {
  public CsvTestDefinitionConfiguration(
      DefinitionResourceLocation inputs,
      DefinitionResourceLocation expectedOutputs,
      String caseIdColumn) {
    this(inputs, expectedOutputs, caseIdColumn, Map.of(), Map.of());
  }

  public CsvTestDefinitionConfiguration {
    inputs = Objects.requireNonNull(inputs, "inputs");
    expectedOutputs = Objects.requireNonNull(expectedOutputs, "expectedOutputs");
    caseIdColumn = Objects.requireNonNull(caseIdColumn, "caseIdColumn").trim();
    if (caseIdColumn.isEmpty())
      throw new IllegalArgumentException("caseIdColumn must not be blank");
    inputSecretFields = Map.copyOf(Objects.requireNonNull(inputSecretFields, "inputSecretFields"));
    expectedOutputSecretFields =
        Map.copyOf(
            Objects.requireNonNull(expectedOutputSecretFields, "expectedOutputSecretFields"));
  }
}
