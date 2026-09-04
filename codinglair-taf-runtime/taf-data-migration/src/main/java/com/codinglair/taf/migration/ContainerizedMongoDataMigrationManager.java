package com.codinglair.taf.migration;

import com.codinglair.taf.runtime.core.migration.DataMigrationManager;
import com.codinglair.taf.runtime.core.migration.MigrationEvidence;
import com.codinglair.taf.runtime.core.migration.MigrationOutcome;
import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import com.codinglair.taf.runtime.core.migration.MigrationRequest;
import com.codinglair.taf.runtime.core.migration.MigrationResult;
import com.codinglair.taf.runtime.core.migration.MigrationTarget;
import com.codinglair.taf.runtime.core.migration.MigrationValidationResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Containerized MongoDB migration manager with automatic context cleanup on failure. */
final class ContainerizedMongoDataMigrationManager implements DataMigrationManager {
  private final MongoContainerMigrationProperties properties;
  private final MongoMigrationJobExecutor jobs;
  private final MongoMigrationCoordinator coordinator;
  private final ObjectMapper mapper;
  private final MongoContextDestroyer contextDestroyer;

  ContainerizedMongoDataMigrationManager(
      MongoContainerMigrationProperties properties,
      MongoMigrationJobExecutor jobs,
      MongoMigrationCoordinator coordinator,
      ObjectMapper mapper) {
    this(properties, jobs, coordinator, mapper, target -> {});
  }

  ContainerizedMongoDataMigrationManager(
      MongoContainerMigrationProperties properties,
      MongoMigrationJobExecutor jobs,
      MongoMigrationCoordinator coordinator,
      ObjectMapper mapper,
      MongoContextDestroyer contextDestroyer) {
    this.properties = properties;
    this.jobs = jobs;
    this.coordinator = coordinator;
    this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.contextDestroyer = contextDestroyer;
  }

  /**
   * Destroys the MongoDB context associated with the last failed migration. This is called
   * automatically when a migration fails to ensure clean state before retry.
   *
   * @return true if a context was destroyed, false if no context was available
   */
  public boolean destroyContext() {
    contextDestroyer.destroy(properties.getTargetIdentity());
    return true;
  }

  @Override
  public MigrationResult execute(MigrationRequest request) {
    if (request.policy() == MigrationPolicy.DISABLED) {
      return new MigrationResult(
          MigrationOutcome.DISABLED, MigrationValidationResult.passed(), List.of());
    }
    if (!request.technology().equalsIgnoreCase("mongodb")
        || request.target() != MigrationTarget.FRAMEWORK_CONTEXT) {
      return failed(
          "Automatic Mongo migration is restricted to the framework-owned container context");
    }
    try {
      properties.validateForExecution();
      MongoContainerMigrationProperties.History configured =
          properties.history(request.targetName());
      List<String> selectedLocations =
          properties.getHistories().isEmpty() ? request.locations() : configured.getLocations();
      List<Path> locations = resolveLocations(selectedLocations);
      String operation = request.policy() == MigrationPolicy.VALIDATE_ONLY ? "validate" : "migrate";
      try (var _ =
          coordinator.acquire(
              properties.getTargetIdentity(),
              configured.getTable(),
              properties.getAcquisitionTimeout())) {
        MongoMigrationJobResult job =
            jobs.execute(job(operation, configured.getTable(), locations));
        return map(request, operation, job);
      }
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      destroyFailedContext();
      return failed("Mongo migration was cancelled while waiting for the logical target owner");
    } catch (RuntimeException _) {
      destroyFailedContext();
      return failed("Mongo migration failed; inspect sanitized logical history evidence");
    }
  }

  private void destroyFailedContext() {
    try {
      destroyContext();
    } catch (RuntimeException _) {
      // Preserve the migration failure diagnostic; the destroyer owns cleanup evidence.
    }
  }

  private MongoMigrationJob job(String operation, String history, List<Path> locations) {
    return new MongoMigrationJob(
        properties.getImage(),
        properties.getNetworkId(),
        properties.getUri(),
        history,
        operation,
        locations,
        properties.getExecutionTimeout(),
        properties.getMaximumOutputBytes(),
        properties.getMemoryBytes(),
        properties.getCpuCount(),
        properties.getPidLimit(),
        properties.getTmpfsBytes());
  }

  private MigrationResult map(
      MigrationRequest request, String operation, MongoMigrationJobResult job) {
    try {
      byte[] bytes = job.output().getBytes(java.nio.charset.StandardCharsets.UTF_8);
      if (bytes.length > properties.getMaximumOutputBytes())
        return failed("Flyway structured output exceeded the configured bound");
      FlywayStructuredOutput output = mapper.readValue(bytes, FlywayStructuredOutput.class);
      boolean structuredSuccess =
          operation.equals("validate")
              ? Boolean.TRUE.equals(output.validationSuccessful())
              : Boolean.TRUE.equals(output.success());
      if (job.exitCode() != 0
          || !structuredSuccess
          || (output.exception() != null && !output.exception().isNull())) {
        return failed("Flyway reported an unsuccessful Mongo migration operation");
      }
      MigrationOutcome outcome =
          operation.equals("validate") ? MigrationOutcome.VALIDATED : MigrationOutcome.MIGRATED;
      List<MigrationEvidence> evidence =
          output.migrations().stream()
              .map(
                  item ->
                      new MigrationEvidence(
                          request.targetName(),
                          value(item.version(), "current"),
                          value(item.checksum(), "unavailable"),
                          outcome,
                          Duration.ofMillis(
                              item.executionTime() == null
                                  ? 0
                                  : Math.max(0, item.executionTime()))))
              .toList();
      return new MigrationResult(outcome, MigrationValidationResult.passed(), evidence);
    } catch (Exception _) {
      return failed("Flyway returned invalid or unsupported structured output");
    }
  }

  private static List<Path> resolveLocations(List<String> configured) {
    return configured.stream()
        .map(
            location -> {
              if (!location.startsWith("filesystem:"))
                throw new IllegalArgumentException(
                    "Mongo migration locations must be filesystem directories");
              Path path =
                  Path.of(location.substring("filesystem:".length())).toAbsolutePath().normalize();
              if (!Files.isDirectory(path) || !Files.isReadable(path))
                throw new IllegalArgumentException("Mongo migration location is unavailable");
              return path;
            })
        .distinct()
        .toList();
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static MigrationResult failed(String diagnostic) {
    return new MigrationResult(
        MigrationOutcome.FAILED, MigrationValidationResult.failed(diagnostic), List.of());
  }
}
