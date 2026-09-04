package com.codinglair.taf.runtime.core.migration;

/** Neutral boundary for validation and materialization of versioned database state. */
@FunctionalInterface
public interface DataMigrationManager {
  MigrationResult execute(MigrationRequest request);
}
