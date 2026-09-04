package com.codinglair.taf.runtime.definition.mongodb;

import com.codinglair.taf.runtime.definition.RepositoryAuthority;
import com.codinglair.taf.runtime.definition.RepositoryConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.bson.Document;

/** Deterministic Git-reviewable snapshot exchange for a single project namespace. */
public final class MongoDefinitionSynchronizer {
  public static final int SNAPSHOT_SCHEMA_VERSION = 1;

  private final MongoTestDefinitionRepository repository;
  private final ObjectMapper mapper;

  public MongoDefinitionSynchronizer(
      MongoTestDefinitionRepository repository, ObjectMapper mapper) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.mapper = Objects.requireNonNull(mapper, "mapper").copy();
    this.mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  public String exportCanonicalJson(RepositoryAuthority authority) {
    Objects.requireNonNull(authority, "authority");
    List<JsonNode> definitions =
        repository.allDocuments().stream()
            .map(this::withoutDatabaseIdentity)
            .sorted(
                Comparator.comparing((JsonNode node) -> node.path("caseId").asText())
                    .thenComparingLong(node -> node.path("version").asLong()))
            .toList();
    try {
      return mapper.writeValueAsString(
              new Snapshot(
                  SNAPSHOT_SCHEMA_VERSION, repository.project(), authority.id(), definitions))
          + System.lineSeparator();
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Definition snapshot cannot be exported", failure);
    }
  }

  public void importCanonicalJson(String json, RepositoryAuthority authority) {
    Objects.requireNonNull(authority, "authority");
    try {
      Snapshot snapshot = mapper.readValue(Objects.requireNonNull(json, "json"), Snapshot.class);
      if (snapshot.schemaVersion() != SNAPSHOT_SCHEMA_VERSION) {
        throw new MongoDefinitionSchemaException(
            "Definition snapshot requires an explicit migration to schema "
                + SNAPSHOT_SCHEMA_VERSION);
      }
      if (!repository.project().equals(snapshot.project())
          || !authority.id().equals(snapshot.authority())) {
        throw new RepositoryConflictException(
            RepositoryConflictException.Kind.AUTHORITY,
            "Snapshot project or authority does not match the target repository");
      }
      List<Document> documents = new ArrayList<>();
      for (JsonNode definition : snapshot.definitions()) {
        documents.add(Document.parse(mapper.writeValueAsString(definition)));
      }
      repository.importDocuments(documents, authority);
    } catch (RepositoryConflictException | MongoDefinitionSchemaException failure) {
      throw failure;
    } catch (JsonProcessingException failure) {
      throw new IllegalArgumentException("Definition snapshot is invalid", failure);
    }
  }

  private JsonNode withoutDatabaseIdentity(Document document) {
    Document copy = new Document(document);
    copy.remove("_id");
    return mapper.valueToTree(copy);
  }

  private record Snapshot(
      int schemaVersion, String project, String authority, List<JsonNode> definitions) {
    private Snapshot {
      project = requireText(project, "project");
      authority = requireText(authority, "authority");
      definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
    }

    private static String requireText(String value, String name) {
      if (value == null || value.isBlank())
        throw new IllegalArgumentException(name + " must not be blank");
      return value.trim();
    }
  }
}
