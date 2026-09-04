package com.codinglair.taf.runtime.definition.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.definition.DefinitionState;
import com.codinglair.taf.runtime.definition.RepositoryAuthority;
import com.codinglair.taf.runtime.definition.RepositoryConflictException;
import com.codinglair.taf.runtime.definition.TestDefinition;
import com.codinglair.taf.runtime.definition.VersionedTestDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@DisplayName("MongoDB context repository integration")
@EnabledIfSystemProperty(named = "taf.mongodb.containers.enabled", matches = "true")
class MongoRepositoryIntegrationTest {
  private static final RepositoryAuthority GIT = new RepositoryAuthority("git");
  private static MongoDBContainer container;
  private static MongoClient client;

  @BeforeAll
  static void startMongo() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the MongoDB repository integration contract");
    container = new MongoDBContainer(DockerImageName.parse("mongo:7.0.24"));
    container.start();
    client = MongoClients.create(container.getReplicaSetUrl());
  }

  @AfterAll
  static void stopMongo() {
    if (client != null) client.close();
    if (container != null) {
      container.stop();
      assertThat(container.isRunning()).isFalse();
    }
  }

  @Nested
  @DisplayName("Namespaces and indexes")
  class NamespacesAndIndexes {
    @Test
    @DisplayName("isolates identical case identifiers by project and creates only approved indexes")
    void isolatesProjectsAndCreatesIndexes() {
      MongoTestDefinitionRepository alpha = repository("alpha");
      MongoTestDefinitionRepository beta = repository("beta");
      alpha.save(definition("TC-1", "alpha"), 0, GIT);
      beta.save(definition("TC-1", "beta"), 0, GIT);

      assertThat(alpha.require("TC-1", Map.class, Map.class).input())
          .containsEntry("owner", "alpha");
      assertThat(beta.require("TC-1", Map.class, Map.class).input()).containsEntry("owner", "beta");
      assertThat(alpha.indexNames())
          .contains(
              "_id_",
              MongoTestDefinitionRepository.VERSION_INDEX,
              MongoTestDefinitionRepository.LATEST_INDEX);
    }

    @Test
    @DisplayName("rejects migration drift without rewriting the stored document")
    void rejectsSchemaDrift() {
      var collection = client.getDatabase("taf_data_002").getCollection("test-definitions");
      collection.insertOne(
          new Document("project", "drift")
              .append("caseId", "TC-1")
              .append("version", 1L)
              .append("schemaVersion", 0));

      assertThatThrownBy(() -> repository("drift"))
          .isInstanceOf(MongoDefinitionSchemaException.class)
          .hasMessageContaining("explicit migration");
      assertThat(
              collection.find(new Document("project", "drift")).first().getInteger("schemaVersion"))
          .isZero();
    }
  }

  @Nested
  @DisplayName("Git synchronization")
  class GitSynchronization {
    @Test
    @DisplayName("exports deterministically and materializes into another isolated project")
    void deterministicMaterialization() {
      MongoTestDefinitionRepository source = repository("source-project");
      source.save(definition("TC-2", "source"), 0, GIT);
      source.save(definition("TC-1", "source"), 0, GIT);
      MongoDefinitionSynchronizer exchange =
          new MongoDefinitionSynchronizer(source, new ObjectMapper());

      String first = exchange.exportCanonicalJson(GIT);
      assertThat(exchange.exportCanonicalJson(GIT)).isEqualTo(first);
      String targetJson = first.replace("source-project", "target-project");
      MongoTestDefinitionRepository target = repository("target-project");
      new MongoDefinitionSynchronizer(target, new ObjectMapper())
          .importCanonicalJson(targetJson, GIT);

      assertThat(target.require("TC-1", Map.class, Map.class).input())
          .containsEntry("owner", "source");
    }

    @Test
    @DisplayName("rejects authority and immutable-content conflicts without overwrite")
    void detectsConflicts() {
      MongoTestDefinitionRepository repository = repository("conflicts");
      MongoDefinitionSynchronizer exchange =
          new MongoDefinitionSynchronizer(repository, new ObjectMapper());
      repository.save(definition("TC-1", "database"), 0, GIT);
      String conflicting = exchange.exportCanonicalJson(GIT).replace("database", "git");

      assertThatThrownBy(() -> exchange.importCanonicalJson(conflicting, GIT))
          .isInstanceOfSatisfying(
              RepositoryConflictException.class,
              failure ->
                  assertThat(failure.kind())
                      .isEqualTo(RepositoryConflictException.Kind.CONCURRENT_WRITE));
      assertThatThrownBy(
              () ->
                  exchange.importCanonicalJson(
                      exchange.exportCanonicalJson(GIT), new RepositoryAuthority("mongodb")))
          .isInstanceOfSatisfying(
              RepositoryConflictException.class,
              failure ->
                  assertThat(failure.kind()).isEqualTo(RepositoryConflictException.Kind.AUTHORITY));
      assertThat(repository.require("TC-1", Map.class, Map.class).input())
          .containsEntry("owner", "database");
    }
  }

  @Nested
  @DisplayName("GridFS payloads")
  class GridFsPayloads {
    @Test
    @DisplayName("streams validated content and deletes staging files after close")
    void streamsAndCleansUp() throws Exception {
      byte[] bytes = "%PDF-gridfs-unchanged".getBytes();
      var bucket = GridFSBuckets.create(client.getDatabase("taf_data_003"));
      ObjectId id =
          bucket.uploadFromStream(
              "sample.pdf",
              new ByteArrayInputStream(bytes),
              new GridFSUploadOptions().metadata(new Document("contentType", "application/pdf")));
      Path temporaryDirectory = Files.createTempDirectory("taf-gridfs-test-");
      String checksum =
          "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      var reference =
          new com.codinglair.taf.runtime.definition.PayloadReference(
              "gridfs-pdf",
              java.net.URI.create("gridfs:" + id.toHexString()),
              checksum,
              "application/pdf",
              bytes.length,
              "v1");

      var resolver = new GridFsPayloadResolver(bucket, temporaryDirectory, 1024);
      try (var payload = resolver.open(reference)) {
        assertThat(payload.stream().readAllBytes()).isEqualTo(bytes);
        try (var staged = Files.list(temporaryDirectory)) {
          assertThat(staged.count()).isOne();
        }
      }
      try (var staged = Files.list(temporaryDirectory)) {
        assertThat(staged.count()).isZero();
      }
      Files.delete(temporaryDirectory);
    }
  }

  private static MongoTestDefinitionRepository repository(String project) {
    MongoTestDefinitionProperties properties = new MongoTestDefinitionProperties();
    properties.setProject(project);
    properties.setAuthority(GIT.id());
    return new MongoTestDefinitionRepository(
        client.getDatabase("taf_data_002"), properties, new ObjectMapper());
  }

  private static VersionedTestDefinition<Map<String, String>, Map<String, String>> definition(
      String caseId, String owner) {
    return new VersionedTestDefinition<>(
        new TestDefinition<>(
            caseId,
            1,
            DefinitionState.DRAFT,
            Map.of("owner", owner),
            Map.of("status", "ok"),
            Map.of()),
        Map.of("trace", "order-7"),
        List.of());
  }
}
