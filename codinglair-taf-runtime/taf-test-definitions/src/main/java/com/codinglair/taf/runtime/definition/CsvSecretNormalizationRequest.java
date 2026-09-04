package com.codinglair.taf.runtime.definition;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Complete explicit request for one consequential CSV preparation operation. */
public record CsvSecretNormalizationRequest(
    Path source,
    String caseIdColumn,
    Map<String, SecretFieldDefinition> secretFields,
    String project,
    String environment,
    String caller,
    boolean authorized) {
  public CsvSecretNormalizationRequest {
    source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
    caseIdColumn = Objects.requireNonNull(caseIdColumn, "caseIdColumn").trim();
    secretFields = Map.copyOf(Objects.requireNonNull(secretFields, "secretFields"));
    project = Objects.requireNonNull(project, "project").trim();
    environment = Objects.requireNonNull(environment, "environment").trim();
    caller = Objects.requireNonNull(caller, "caller").trim();
    if (caseIdColumn.isEmpty()
        || secretFields.isEmpty()
        || project.isEmpty()
        || environment.isEmpty()
        || caller.isEmpty()) {
      throw new IllegalArgumentException("Normalization request metadata must be complete");
    }
  }
}
