package com.codinglair.taf.runtime.definition.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.definition.DefinitionState;
import com.codinglair.taf.runtime.definition.TestDefinitionRepository;
import com.codinglair.taf.runtime.definition.TestDefinitionRepositoryContract;
import com.codinglair.taf.runtime.definition.VersionedTestDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@DisplayName("MongoDB repository contract")
@EnabledIfSystemProperty(named = "taf.mongodb.containers.enabled", matches = "true")
class MongoRepositoryContractTest extends TestDefinitionRepositoryContract {
  private static MongoDBContainer container;
  private static MongoClient client;

  @BeforeAll
  static void startMongo() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the MongoDB repository contract");
    try {
      container = new MongoDBContainer(DockerImageName.parse("mongo:7.0.24"));
      container.start();
      client = MongoClients.create(container.getReplicaSetUrl());
    } catch (RuntimeException failure) {
      stopMongo();
      throw failure;
    }
  }

  @AfterAll
  static void stopMongo() {
    if (client != null) {
      client.close();
      client = null;
    }
    if (container != null) {
      container.stop();
      container = null;
    }
  }

  @Override
  protected TestDefinitionRepository repository(Path file) {
    MongoTestDefinitionProperties properties = new MongoTestDefinitionProperties();
    properties.setProject(file.getParent().getFileName().toString());
    properties.setAuthority(AUTHORITY.id());
    return new MongoTestDefinitionRepository(
        client.getDatabase("taf_contract"), properties, new ObjectMapper());
  }

  @Override
  protected String extension() {
    return "json";
  }

  @Override
  protected void assertStableRoundTrip(
      Path repositoryFile,
      TestDefinitionRepository genericRepository,
      VersionedTestDefinition<Input, Expected> definition) {
    MongoTestDefinitionRepository repository = (MongoTestDefinitionRepository) genericRepository;
    MongoDefinitionSynchronizer synchronizer =
        new MongoDefinitionSynchronizer(repository, new ObjectMapper());
    String first = synchronizer.exportCanonicalJson(AUTHORITY);
    assertThat(synchronizer.exportCanonicalJson(AUTHORITY)).isEqualTo(first);
    repository.transition(
        "TC-1", DefinitionState.REVIEW, 1, AUTHORITY, Input.class, Expected.class);
    assertThat(synchronizer.exportCanonicalJson(AUTHORITY)).startsWith(first.substring(0, 20));
  }
}
