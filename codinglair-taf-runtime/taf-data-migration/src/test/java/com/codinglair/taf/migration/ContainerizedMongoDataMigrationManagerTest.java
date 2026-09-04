package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.migration.MigrationOutcome;
import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import com.codinglair.taf.runtime.core.migration.MigrationRequest;
import com.codinglair.taf.runtime.core.migration.MigrationTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerizedMongoDataMigrationManagerTest {
  @TempDir Path migrations;

  @Test
  void mapsSuccessfulMigrateAndBuildsHardenedDigestPinnedRequest() {
    AtomicReference<MongoMigrationJob> captured = new AtomicReference<>();
    var manager = manager(captured, 0, migrateJson(true));

    var result = manager.execute(request(MigrationPolicy.VALIDATE_AND_MIGRATE));

    assertThat(result.outcome()).isEqualTo(MigrationOutcome.MIGRATED);
    assertThat(result.evidence())
        .singleElement()
        .satisfies(
            evidence -> {
              assertThat(evidence.targetName()).isEqualTo("taf-context");
              assertThat(evidence.version()).isEqualTo("1");
              assertThat(evidence.duration()).isEqualTo(Duration.ofMillis(12));
            });
    assertThat(captured.get().image()).isEqualTo(MongoContainerMigrationProperties.APPROVED_IMAGE);
    assertThat(captured.get().uri()).contains("context_user");
    assertThat(captured.get().toString()).doesNotContain("context_user", "context_password");
    assertThat(captured.get().locations()).containsExactly(migrations.toAbsolutePath().normalize());
  }

  @Test
  void treatsStructuredValidationFailureAsFailureWhenExitIsZero() {
    var manager = manager(new AtomicReference<>(), 0, validateJson(false));
    var result = manager.execute(request(MigrationPolicy.VALIDATE_ONLY));
    assertThat(result.outcome()).isEqualTo(MigrationOutcome.FAILED);
    assertThat(result.validation().diagnostics()).doesNotHaveToString("context_password");
  }

  @Test
  void rejectsInvalidJsonNonZeroExitOversizedOutputAndExternalTargets() {
    assertThat(
            manager(new AtomicReference<>(), 0, "not-json")
                .execute(request(MigrationPolicy.VALIDATE_ONLY))
                .outcome())
        .isEqualTo(MigrationOutcome.FAILED);
    assertThat(
            manager(new AtomicReference<>(), 9, migrateJson(true))
                .execute(request(MigrationPolicy.VALIDATE_AND_MIGRATE))
                .outcome())
        .isEqualTo(MigrationOutcome.FAILED);
    String oversized = "x".repeat(1_048_577);
    assertThat(
            manager(new AtomicReference<>(), 0, oversized)
                .execute(request(MigrationPolicy.VALIDATE_ONLY))
                .outcome())
        .isEqualTo(MigrationOutcome.FAILED);
    MigrationRequest external =
        new MigrationRequest(
            "taf-context",
            "mongodb",
            MigrationTarget.EXTERNAL_SHARED_SUT,
            MigrationPolicy.VALIDATE_ONLY,
            List.of("filesystem:" + migrations));
    assertThat(manager(new AtomicReference<>(), 0, validateJson(true)).execute(external).outcome())
        .isEqualTo(MigrationOutcome.FAILED);
  }

  @Test
  void rejectsMutableImageTlsAndUnknownStructuredFields() {
    MongoContainerMigrationProperties invalid = properties();
    invalid.setImage("flyway/flyway:13.0.0-mongo");
    assertThat(
            manager(invalid, _ -> null).execute(request(MigrationPolicy.VALIDATE_ONLY)).outcome())
        .isEqualTo(MigrationOutcome.FAILED);

    invalid = properties();
    invalid.setUri("mongodb://context_user:context_password@mongo/taf?tls=true");
    assertThat(
            manager(invalid, _ -> null).execute(request(MigrationPolicy.VALIDATE_ONLY)).outcome())
        .isEqualTo(MigrationOutcome.FAILED);

    assertThat(
            manager(
                    new AtomicReference<>(),
                    0,
                    validateJson(true)
                        .replace("\"warnings\":[]", "\"warnings\":[],\"unexpected\":true"))
                .execute(request(MigrationPolicy.VALIDATE_ONLY))
                .outcome())
        .isEqualTo(MigrationOutcome.FAILED);
  }

  private ContainerizedMongoDataMigrationManager manager(
      AtomicReference<MongoMigrationJob> captured, int exit, String output) {
    return manager(
        properties(),
        job -> {
          captured.set(job);
          return new MongoMigrationJobResult(exit, output, Duration.ofMillis(20));
        });
  }

  private static ContainerizedMongoDataMigrationManager manager(
      MongoContainerMigrationProperties properties, MongoMigrationJobExecutor jobs) {
    return new ContainerizedMongoDataMigrationManager(
        properties, jobs, new MongoMigrationCoordinator(), new ObjectMapper());
  }

  private MongoContainerMigrationProperties properties() {
    var value = new MongoContainerMigrationProperties();
    value.setEnabled(true);
    value.setImage(MongoContainerMigrationProperties.APPROVED_IMAGE);
    value.setTargetIdentity("taf-context");
    value.setHistory("taf_framework_history");
    value.setNetworkId("isolated-network");
    value.setUri("mongodb://context_user:context_password@mongo/taf-context");
    return value;
  }

  private MigrationRequest request(MigrationPolicy policy) {
    return new MigrationRequest(
        "taf-context",
        "mongodb",
        MigrationTarget.FRAMEWORK_CONTEXT,
        policy,
        List.of("filesystem:" + migrations));
  }

  private static String migrateJson(boolean success) {
    return """
        {"database":"taf","databaseType":"MongoDB","flywayVersion":"13.0.0",
        "initialSchemaVersion":null,"licenseFailed":false,"migrationsExecuted":1,
        "operation":"migrate","schemaName":"","success":%s,"validationSuccessful":null,
        "targetSchemaVersion":"1","totalMigrationTime":12,
        "migrations":[{"category":"Versioned","description":"baseline","executionTime":12,
        "filepath":"/taf-migrations/0/V1__baseline.json","type":"SQL","version":"1",
        "checksum":"123","state":"Success"}],"timestamp":"2026-08-14T00:00:00","exception":null,"warnings":[]}
        """
        .formatted(success);
  }

  private static String validateJson(boolean valid) {
    return """
        {"database":"taf","databaseType":"MongoDB","flywayVersion":"13.0.0",
        "initialSchemaVersion":"1","licenseFailed":false,"migrationsExecuted":0,
        "operation":"validate","schemaName":"","success":true,"validationSuccessful":%s,
        "targetSchemaVersion":"1","totalMigrationTime":1,"migrations":[],
        "timestamp":"2026-08-14T00:00:00","exception":null,"warnings":[]}
        """
        .formatted(valid);
  }
}
