package com.codinglair.taf.migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
record FlywayStructuredOutput(
    String allErrorMessages,
    String database,
    String databaseType,
    String flywayVersion,
    String initialSchemaVersion,
    Boolean licenseFailed,
    Long migrationsExecuted,
    String operation,
    String schemaName,
    Boolean success,
    Boolean validationSuccessful,
    Long validateCount,
    JsonNode errorDetails,
    List<JsonNode> invalidMigrations,
    String targetSchemaVersion,
    Long totalMigrationTime,
    List<FlywayMigrationOutput> migrations,
    String timestamp,
    JsonNode exception,
    List<String> warnings) {
  FlywayStructuredOutput {
    migrations = migrations == null ? List.of() : List.copyOf(migrations);
    invalidMigrations = invalidMigrations == null ? List.of() : List.copyOf(invalidMigrations);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}

@JsonIgnoreProperties(ignoreUnknown = false)
record FlywayMigrationOutput(
    String category,
    String description,
    Long executionTime,
    String filepath,
    String type,
    String version,
    String checksum,
    String state) {}
