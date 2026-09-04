package com.codinglair.taf.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.migration.MigrationOutcome;
import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import com.codinglair.taf.runtime.core.migration.MigrationRequest;
import com.codinglair.taf.runtime.core.migration.MigrationTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "taf.migration.containers", matches = "true")
class ContainerizedMongoMigrationIntegrationTest {
  private static final String MONGO_IMAGE =
      "mongo@sha256:05b417e0f4e6c30f4d5bf8ef47cdb846ba377b366277b925493d4c42339eb533";

  @TempDir Path tempDir;

  @BeforeAll
  static void requireDocker() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the MongoDB migration container contract");
  }

  @Test
  void frameworkAndConsumerHistoriesRunConcurrentlyAndRemainIsolated() throws Exception {
    try (Network network =
            Network.builder()
                .createNetworkCmdModifier(command -> command.withInternal(true))
                .build();
        GenericContainer<?> mongo =
            new GenericContainer<>(DockerImageName.parse(MONGO_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("taf-mongo")
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))) {
      mongo.start();
      Path framework = Files.createDirectories(tempDir.resolve("framework"));
      Path consumer = Files.createDirectories(tempDir.resolve("consumer"));
      Files.writeString(
          framework.resolve("V1__framework.json"),
          "{\"insert\":\"framework_marker\",\"documents\":[{\"owner\":\"framework\"}]}");
      Files.writeString(
          consumer.resolve("V1__consumer.json"),
          "{\"insert\":\"consumer_marker\",\"documents\":[{\"owner\":\"consumer\"}]}");
      var executor = new TestcontainersMongoMigrationJobExecutor();
      var pool = Executors.newFixedThreadPool(2);
      try {
        var f =
            pool.submit(
                () -> executor.execute(job(network.getId(), framework, "taf_framework_history")));
        var c =
            pool.submit(
                () -> executor.execute(job(network.getId(), consumer, "taf_consumer_history")));
        assertThat(f.get(3, TimeUnit.MINUTES).exitCode()).isZero();
        assertThat(c.get(3, TimeUnit.MINUTES).exitCode()).isZero();
      } finally {
        pool.shutdownNow();
      }
      var frameworkInspection =
          mongo.execInContainer(
              "mongosh",
              "--quiet",
              "mongodb://localhost/taf_framework",
              "--eval",
              "JSON.stringify({h:db.taf_framework_history.countDocuments({success:true}),m:db.framework_marker.countDocuments({})})");
      var consumerInspection =
          mongo.execInContainer(
              "mongosh",
              "--quiet",
              "mongodb://localhost/taf_consumer",
              "--eval",
              "JSON.stringify({h:db.taf_consumer_history.countDocuments({success:true}),m:db.consumer_marker.countDocuments({})})");
      assertThat(frameworkInspection.getExitCode()).isZero();
      assertThat(consumerInspection.getExitCode()).isZero();
      assertThat(frameworkInspection.getStdout()).contains("\"h\":2", "\"m\":1");
      assertThat(consumerInspection.getStdout()).contains("\"h\":2", "\"m\":1");
    }
  }

  private static MongoMigrationJob job(String networkId, Path location, String history) {
    return new MongoMigrationJob(
        MongoContainerMigrationProperties.APPROVED_IMAGE,
        networkId,
        "mongodb://taf-mongo:27017/"
            + (history.contains("framework") ? "taf_framework" : "taf_consumer"),
        history,
        "migrate",
        List.of(location),
        java.time.Duration.ofMinutes(2),
        1_048_576,
        536_870_912,
        1,
        256,
        67_108_864);
  }

  @Test
  void migratesAndValidatesOnAnInternalNetworkWithNoJobLeftRunning() throws Exception {
    try (Network network =
            Network.builder()
                .createNetworkCmdModifier(command -> command.withInternal(true))
                .build();
        GenericContainer<?> mongo =
            new GenericContainer<>(DockerImageName.parse(MONGO_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("taf-mongo")
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))) {
      mongo.start();
      Path migrations =
          Path.of("src/test/resources/db/migration/mongodb/framework").toAbsolutePath();
      var properties = properties(network.getId(), migrations);
      var captured = new AtomicReference<MongoMigrationJobResult>();
      MongoMigrationJobExecutor delegate = new TestcontainersMongoMigrationJobExecutor();
      var manager =
          new ContainerizedMongoDataMigrationManager(
              properties,
              job -> {
                MongoMigrationJobResult result = delegate.execute(job);
                captured.set(result);
                return result;
              },
              new MongoMigrationCoordinator(),
              new ObjectMapper());

      var migrated = manager.execute(request(migrations, MigrationPolicy.VALIDATE_AND_MIGRATE));
      var validated = manager.execute(request(migrations, MigrationPolicy.VALIDATE_ONLY));

      assertThat(migrated.outcome()).isEqualTo(MigrationOutcome.MIGRATED);
      assertThat(validated.outcome())
          .as(captured.get().output())
          .isEqualTo(MigrationOutcome.VALIDATED);
      var inspection =
          mongo.execInContainer(
              "mongosh",
              "--quiet",
              "mongodb://localhost/taf_mig",
              "--eval",
              "JSON.stringify({collections:db.getCollectionNames(),history:db.taf_framework_history.countDocuments({success:true})})");
      assertThat(inspection.getExitCode()).isZero();
      assertThat(inspection.getStdout()).contains("taf_definitions", "\"history\":3");
    }
  }

  private static MongoContainerMigrationProperties properties(String networkId, Path migrations) {
    var value = new MongoContainerMigrationProperties();
    value.setEnabled(true);
    value.setImage(MongoContainerMigrationProperties.APPROVED_IMAGE);
    value.setTargetIdentity("taf_mig");
    value.setHistory("taf_framework_history");
    value.setLocations(List.of("filesystem:" + migrations));
    value.setNetworkId(networkId);
    value.setUri("mongodb://taf-mongo:27017/taf_mig");
    return value;
  }

  private static MigrationRequest request(Path migrations, MigrationPolicy policy) {
    return new MigrationRequest(
        "taf_mig",
        "mongodb",
        MigrationTarget.FRAMEWORK_CONTEXT,
        policy,
        List.of("filesystem:" + migrations));
  }
}
