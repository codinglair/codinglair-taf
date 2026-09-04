package com.codinglair.taf.runtime.definition.mongodb;

import com.codinglair.taf.runtime.definition.PayloadProviderContract;
import com.codinglair.taf.runtime.definition.PayloadReference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "taf.mongodb.containers.enabled", matches = "true")
class GridFsPayloadProviderContractTest extends PayloadProviderContract {
  private static MongoDBContainer container;
  private static MongoClient client;

  @BeforeAll
  static void start() {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the GridFS payload contract");
    container = new MongoDBContainer(DockerImageName.parse("mongo:7.0.24"));
    container.start();
    client = MongoClients.create(container.getReplicaSetUrl());
  }

  @AfterAll
  static void stop() {
    if (client != null) client.close();
    if (container != null) container.stop();
  }

  @Override
  protected Fixture fixture(byte[] bytes, String mediaType) throws Exception {
    var bucket = GridFSBuckets.create(client.getDatabase("taf_payload_contract"));
    var id =
        bucket.uploadFromStream(
            "contract.pdf",
            new ByteArrayInputStream(bytes),
            new GridFSUploadOptions().metadata(new Document("contentType", mediaType)));
    Path temporaryDirectory = Files.createTempDirectory("taf-gridfs-contract-");
    return new Fixture(
        new GridFsPayloadResolver(bucket, temporaryDirectory, 1024),
        new PayloadReference(
            "contract",
            URI.create("gridfs:" + id.toHexString()),
            checksum(bytes),
            mediaType,
            bytes.length,
            "v1"),
        () -> {
          try (var files = Files.list(temporaryDirectory)) {
            if (files.findAny().isPresent()) throw new IllegalStateException("staging file leaked");
          }
          Files.delete(temporaryDirectory);
          bucket.delete(id);
        });
  }
}
