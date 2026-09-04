package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.migration.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test proving automatic container destruction on migration failure. Ensures clean
 * state before retry.
 */
@DisplayName("Migration Failure Cleanup")
class MigrationFailureCleanupTest {

  @Nested
  @DisplayName("Failure Cleanup")
  class FailureCleanup {

    @TempDir Path tempDir;

    @Test
    @DisplayName("Failed migration destroys the owning context before returning")
    void failedMigrationTracksContext() throws Exception {
      Path migrations = tempDir.resolve("migrations");
      Files.createDirectories(migrations);

      String networkId = "test-network";
      String targetIdentity = "test-target";
      String historyTable = "test_history";

      var properties = properties(networkId, targetIdentity, historyTable, migrations);

      var destroyed = new AtomicReference<String>();
      var manager =
          new ContainerizedMongoDataMigrationManager(
              properties,
              job -> {
                throw new IllegalStateException("failure");
              },
              new MongoMigrationCoordinator(),
              new ObjectMapper(),
              destroyed::set);
      var result =
          manager.execute(
              new MigrationRequest(
                  targetIdentity,
                  "mongodb",
                  MigrationTarget.FRAMEWORK_CONTEXT,
                  MigrationPolicy.VALIDATE_AND_MIGRATE,
                  List.of("filesystem:" + migrations)));
      assertThat(result.outcome()).isEqualTo(MigrationOutcome.FAILED);
      assertThat(destroyed).hasValue(targetIdentity);
    }

    @Test
    @DisplayName("Cleanup returns true when context exists")
    void cleanupReturnsTrueWhenContextExists() throws Exception {
      Path migrations = tempDir.resolve("migrations");
      Files.createDirectories(migrations);

      String networkId = "test-network";
      String targetIdentity = "test-target";
      String historyTable = "test_history";

      var properties = properties(networkId, targetIdentity, historyTable, migrations);

      var destroyed = new AtomicReference<String>();
      var manager =
          new ContainerizedMongoDataMigrationManager(
              properties,
              job -> new MongoMigrationJobResult(0, "{}", Duration.ZERO),
              new MongoMigrationCoordinator(),
              new ObjectMapper(),
              destroyed::set);
      assertThat(manager.destroyContext()).isTrue();
      assertThat(destroyed).hasValue(targetIdentity);
    }

    @Test
    @DisplayName("Cleanup is idempotent")
    void cleanupIsIdempotent() throws Exception {
      Path migrations = tempDir.resolve("migrations-idempotent");
      Files.createDirectories(migrations);
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      var manager =
          new ContainerizedMongoDataMigrationManager(
              properties("network1", "target1", "history1", migrations),
              job -> new MongoMigrationJobResult(0, "{}", Duration.ZERO),
              new MongoMigrationCoordinator(),
              new ObjectMapper(),
              target -> calls.incrementAndGet());
      assertThat(manager.destroyContext()).isTrue();
      assertThat(manager.destroyContext()).isTrue();
      assertThat(calls).hasValue(2);
    }
  }

  private static MongoContainerMigrationProperties properties(
      String networkId, String targetIdentity, String historyTable, Path migrations) {
    var value = new MongoContainerMigrationProperties();
    value.setEnabled(true);
    value.setImage(MongoContainerMigrationProperties.APPROVED_IMAGE);
    value.setTargetIdentity(targetIdentity);
    value.setHistory(historyTable);
    value.setLocations(java.util.List.of("filesystem:" + migrations));
    value.setNetworkId(networkId);
    value.setUri("mongodb://localhost:27017/testdb");
    return value;
  }
}
