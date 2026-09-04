package com.codinglair.taf.migration;

import com.codinglair.taf.runtime.core.migration.DataMigrationManager;
import com.codinglair.taf.runtime.core.migration.MigrationOutcome;
import com.codinglair.taf.runtime.core.migration.MigrationRequest;
import com.codinglair.taf.runtime.core.migration.MigrationResult;
import com.codinglair.taf.runtime.core.migration.MigrationTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;

/** Idempotent readiness gate that materializes the context before repository construction. */
public final class MongoContextMigration {
  private final DataMigrationManager manager;
  private final MongoContainerMigrationProperties properties;
  private MigrationResult completed;
  private boolean unusable;

  MongoContextMigration(
      DataMigrationManager manager, MongoContainerMigrationProperties properties) {
    this.manager = manager;
    this.properties = properties;
  }

  /** Creates the approved containerized framework-context preflight for explicit consumers. */
  public static MongoContextMigration containerized(
      MongoContainerMigrationProperties properties,
      ObjectMapper objectMapper,
      MongoContextDestroyer contextDestroyer) {
    return new MongoContextMigration(
        new ContainerizedMongoDataMigrationManager(
            properties,
            new TestcontainersMongoMigrationJobExecutor(),
            new MongoMigrationCoordinator(),
            objectMapper,
            contextDestroyer),
        properties);
  }

  public synchronized MigrationResult ensureReady() {
    if (unusable)
      throw new IllegalStateException("The Mongo context is unusable and must be destroyed");
    if (completed != null) return completed;
    var evidence = new ArrayList<com.codinglair.taf.runtime.core.migration.MigrationEvidence>();
    if (properties.getHistories().isEmpty()) {
      completed = execute(properties.getTargetIdentity(), properties.getLocations());
      return completed;
    }
    for (var entry : properties.getHistories().entrySet()) {
      MigrationResult result = execute(entry.getKey(), entry.getValue().getLocations());
      evidence.addAll(result.evidence());
    }
    completed =
        new MigrationResult(
            MigrationOutcome.MIGRATED,
            com.codinglair.taf.runtime.core.migration.MigrationValidationResult.passed(),
            evidence);
    return completed;
  }

  private MigrationResult execute(String identity, java.util.List<String> locations) {
    MigrationResult result =
        manager.execute(
            new MigrationRequest(
                identity,
                "mongodb",
                MigrationTarget.FRAMEWORK_CONTEXT,
                properties.getPolicy(),
                locations));
    if (!result.validation().valid() || result.outcome() == MigrationOutcome.FAILED) {
      unusable = true;
      throw new IllegalStateException(
          "Mongo context migration failed; destroy the scoped context before retry");
    }
    return result;
  }

  public synchronized boolean isUnusable() {
    return unusable;
  }
}
