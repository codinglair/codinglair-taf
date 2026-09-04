package com.codinglair.taf.demo.sauce.integration;

import com.codinglair.taf.migration.MongoContainerMigrationProperties;
import com.codinglair.taf.migration.MongoContextMigration;
import com.codinglair.taf.runtime.core.migration.MigrationEvidence;
import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import com.codinglair.taf.runtime.definition.mongodb.MongoTestDefinitionProperties;
import com.codinglair.taf.runtime.definition.mongodb.MongoTestDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.file.Path;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class MongoDefinitionFixture implements AutoCloseable {
  private static final String IMAGE =
      "mongo@sha256:05b417e0f4e6c30f4d5bf8ef47cdb846ba377b366277b925493d4c42339eb533";
  private final Network network = Network.newNetwork();
  private final GenericContainer<?> container =
      new GenericContainer<>(DockerImageName.parse(IMAGE))
          .withNetwork(network)
          .withNetworkAliases("taf-context-mongo")
          .withExposedPorts(27017)
          .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1));
  private MongoClient client;
  private List<MigrationEvidence> migrationEvidence = List.of();

  public TestDefinitionResolver start() throws Exception {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the Sauce Demo MongoDB integration contracts");
    container.start();
    MongoContextMigration migrationGate = migrationGate(MigrationPolicy.VALIDATE_AND_MIGRATE, null);
    migrationEvidence = migrationGate.ensureReady().evidence();
    if (migrationGate.ensureReady().evidence() != migrationEvidence) {
      throw new AssertionError("Mongo migration preflight was repeated");
    }
    client =
        MongoClients.create(
            "mongodb://" + container.getHost() + ":" + container.getMappedPort(27017));
    MongoTestDefinitionProperties properties = new MongoTestDefinitionProperties();
    properties.setDatabase("taf-context");
    properties.setCollection("taf_definitions");
    properties.setProject("sauce-demo");
    MongoTestDefinitionRepository repository =
        new MongoTestDefinitionRepository(
            client.getDatabase("taf-context"), properties, new ObjectMapper());
    if (!(repository instanceof MongoTestDefinitionRepository)) {
      throw new AssertionError("Mongo repository identity was not retained");
    }
    return new TestDefinitionResolver(repository);
  }

  public List<MigrationEvidence> migrationEvidence() {
    return migrationEvidence;
  }

  public List<Document> definitions() {
    return client
        .getDatabase("taf-context")
        .getCollection("taf_definitions")
        .find()
        .into(new java.util.ArrayList<>());
  }

  public long successfulHistoryEntries(String history) {
    return client
        .getDatabase("taf-context")
        .getCollection(history)
        .countDocuments(new Document("success", true));
  }

  public void validate(Path consumerLocation) {
    migrationGate(MigrationPolicy.VALIDATE_ONLY, consumerLocation).ensureReady();
  }

  private MongoContextMigration migrationGate(MigrationPolicy policy, Path consumerLocation) {
    MongoContainerMigrationProperties properties = new MongoContainerMigrationProperties();
    properties.setEnabled(true);
    properties.setImage(MongoContainerMigrationProperties.APPROVED_IMAGE);
    properties.setTargetIdentity("taf-context");
    properties.setNetworkId(network.getId());
    properties.setUri("mongodb://taf-context-mongo:27017/taf-context");
    properties.setPolicy(policy);
    var history = new MongoContainerMigrationProperties.History();
    history.setTable("taf_context_history");
    history.setLocations(
        List.of(
            "filesystem:"
                + Path.of(
                        "../../codinglair-taf-runtime/taf-data-migration/src/test/resources/db/migration/mongodb/framework")
                    .toAbsolutePath()
                    .normalize(),
            "filesystem:"
                + (consumerLocation == null
                        ? Path.of("src/test/resources/db/migration/mongodb/sauce-demo")
                        : consumerLocation)
                    .toAbsolutePath()
                    .normalize()));
    properties.getHistories().put("taf-context", history);
    return MongoContextMigration.containerized(
        properties, new ObjectMapper(), (String _) -> container.stop());
  }

  @Override
  public void close() {
    if (client != null) client.close();
    container.stop();
    network.close();
  }
}
