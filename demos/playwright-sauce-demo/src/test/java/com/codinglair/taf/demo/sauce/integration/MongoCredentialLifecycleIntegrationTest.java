package com.codinglair.taf.demo.sauce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.migration.CredentialManager;
import com.codinglair.taf.migration.MongoCredentialStore;
import com.mongodb.client.MongoClients;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "taf.migration.containers", matches = "true")
class MongoCredentialLifecycleIntegrationTest {
  private static final String IMAGE =
      "mongo@sha256:05b417e0f4e6c30f4d5bf8ef47cdb846ba377b366277b925493d4c42339eb533";

  @Test
  @TestCaseId("TC0903")
  void createsDatabaseScopedUserAndDropsItWithLease() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the MongoDB credential lifecycle contract");
    try (GenericContainer<?> mongo =
        new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withExposedPorts(27017)
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))) {
      mongo.start();
      try (com.mongodb.client.MongoClient client =
          MongoClients.create("mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017))) {
        com.mongodb.client.MongoDatabase database = client.getDatabase("taf_credential_scope");
        MongoCredentialStore store =
            new MongoCredentialStore() {
              public void createDatabaseUser(String ignored, String username, String password) {
                database.runCommand(
                    new Document("createUser", username)
                        .append("pwd", password)
                        .append(
                            "roles",
                            List.of(
                                new Document("role", "readWrite")
                                    .append("db", "taf_credential_scope"))));
              }

              public void dropDatabaseUser(String ignored, String username) {
                database.runCommand(new Document("dropUser", username));
              }
            };
        CredentialManager manager = new CredentialManager(60_000, store);
        manager.create("taf_credential_scope", "taf_migration_lease", "protected-value", "LEASE_1");
        assertThat(
                database
                    .runCommand(new Document("usersInfo", "taf_migration_lease"))
                    .getList("users", Document.class))
            .hasSize(1);
        manager.cleanupDatabase("taf_credential_scope");
        assertThat(
                database
                    .runCommand(new Document("usersInfo", "taf_migration_lease"))
                    .getList("users", Document.class))
            .isEmpty();
      }
    }
  }
}
