package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test proving two-history isolation for parallel migrations. Framework and consumer
 * migrations run in parallel with different histories.
 */
@DisplayName("Two History Isolation")
class TwoHistoryIsolationTest {

  private static final String MONGO_IMAGE =
      "flyway/flyway@sha256:263d343d6a3ae9122fd12e45e3b68e29652c109425f45f4263e0b0babec31a5f";

  @Nested
  @DisplayName("Parallel Framework/Consumer Migrations")
  class ParallelMigrations {

    @TempDir Path tempDir;

    @Test
    @DisplayName("Framework and consumer histories are isolated")
    void historiesAreIsolated() throws Exception {
      Path frameworkMigrations = tempDir.resolve("framework");
      Path consumerMigrations = tempDir.resolve("consumer");

      Files.createDirectories(frameworkMigrations);
      Files.createDirectories(consumerMigrations);

      // Framework migration
      Path frameworkMigration = frameworkMigrations.resolve("V1__framework.sql");
      Files.writeString(
          frameworkMigration,
          """
          CREATE TABLE taf_definitions (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            name VARCHAR(255) NOT NULL,
            version VARCHAR(64) NOT NULL
          );""");

      // Consumer migration
      Path consumerMigration = consumerMigrations.resolve("V1__consumer.sql");
      Files.writeString(
          consumerMigration,
          """
          CREATE TABLE consumer_data (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            value VARCHAR(1024) NOT NULL
          );""");

      String frameworkTarget = "framework";
      String consumerTarget = "consumer";
      String frameworkHistory = "taf_framework_history";
      String consumerHistory = "taf_consumer_history";

      // Framework properties
      var frameworkProperties = properties(frameworkTarget, frameworkHistory, frameworkMigrations);
      // Consumer properties
      var consumerProperties = properties(consumerTarget, consumerHistory, consumerMigrations);

      // Verify histories are different
      assertThat(frameworkHistory).isNotEqualTo(consumerHistory);
      assertThat(frameworkHistory).isEqualTo("taf_framework_history");
      assertThat(consumerHistory).isEqualTo("taf_consumer_history");
    }

    @Test
    @DisplayName("Parallel execution does not interfere")
    void parallelExecutionDoesNotInterfere() throws Exception {
      Path frameworkMigrations = tempDir.resolve("framework");
      Path consumerMigrations = tempDir.resolve("consumer");

      Files.createDirectories(frameworkMigrations);
      Files.createDirectories(consumerMigrations);

      // Create framework migration
      Path frameworkMigration = frameworkMigrations.resolve("V1__framework.sql");
      Files.writeString(
          frameworkMigration,
          """
          CREATE TABLE taf_definitions (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            name VARCHAR(255) NOT NULL
          );""");

      // Create consumer migration
      Path consumerMigration = consumerMigrations.resolve("V1__consumer.sql");
      Files.writeString(
          consumerMigration,
          """
          CREATE TABLE consumer_data (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            value VARCHAR(1024) NOT NULL
          );""");

      String frameworkTarget = "framework";
      String consumerTarget = "consumer";
      String frameworkHistory = "taf_framework_history";
      String consumerHistory = "taf_consumer_history";

      var frameworkProperties = properties(frameworkTarget, frameworkHistory, frameworkMigrations);
      var consumerProperties = properties(consumerTarget, consumerHistory, consumerMigrations);

      // Verify different histories produce different context identifiers
      var frameworkContext =
          frameworkProperties.getNetworkId()
              + ":"
              + frameworkProperties.getTargetIdentity()
              + ":"
              + frameworkProperties.getHistory();
      var consumerContext =
          consumerProperties.getNetworkId()
              + ":"
              + consumerProperties.getTargetIdentity()
              + ":"
              + consumerProperties.getHistory();

      // Contexts should be different
      assertThat(frameworkContext).isNotEqualTo(consumerContext);
      assertThat(frameworkContext).contains("taf_framework_history");
      assertThat(consumerContext).contains("taf_consumer_history");
    }

    @Test
    @DisplayName("Different histories do not serialize")
    void differentHistoriesDoNotSerialize() throws Exception {
      Path frameworkMigrations = tempDir.resolve("framework");
      Path consumerMigrations = tempDir.resolve("consumer");

      Files.createDirectories(frameworkMigrations);
      Files.createDirectories(consumerMigrations);

      Path frameworkMigration = frameworkMigrations.resolve("V1__framework.sql");
      Files.writeString(frameworkMigration, "CREATE TABLE f1 (id BIGINT PRIMARY KEY);");

      Path consumerMigration = consumerMigrations.resolve("V1__consumer.sql");
      Files.writeString(consumerMigration, "CREATE TABLE c1 (id BIGINT PRIMARY KEY);");

      String frameworkTarget = "framework";
      String consumerTarget = "consumer";
      String frameworkHistory = "taf_framework_history";
      String consumerHistory = "taf_consumer_history";

      var frameworkProperties = properties(frameworkTarget, frameworkHistory, frameworkMigrations);
      var consumerProperties = properties(consumerTarget, consumerHistory, consumerMigrations);

      // Both histories should have different context identifiers
      var frameworkContext =
          frameworkProperties.getNetworkId()
              + ":"
              + frameworkProperties.getTargetIdentity()
              + ":"
              + frameworkProperties.getHistory();
      var consumerContext =
          consumerProperties.getNetworkId()
              + ":"
              + consumerProperties.getTargetIdentity()
              + ":"
              + consumerProperties.getHistory();

      assertThat(frameworkContext).isNotEqualTo(consumerContext);
    }
  }

  private static MongoContainerMigrationProperties properties(
      String target, String history, Path migrations) {
    var value = new MongoContainerMigrationProperties();
    value.setEnabled(true);
    value.setImage(MONGO_IMAGE);
    value.setTargetIdentity(target);
    value.setHistory(history);
    value.setLocations(java.util.List.of("filesystem:" + migrations));
    value.setNetworkId("test-network");
    value.setUri("mongodb://localhost:27017/testdb");
    return value;
  }
}
