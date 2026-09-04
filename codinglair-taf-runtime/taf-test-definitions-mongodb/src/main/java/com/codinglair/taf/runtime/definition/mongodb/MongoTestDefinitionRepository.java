package com.codinglair.taf.runtime.definition.mongodb;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.compoundIndex;
import static com.mongodb.client.model.Indexes.descending;

import com.codinglair.taf.runtime.definition.DefinitionDiagnosticException;
import com.codinglair.taf.runtime.definition.DefinitionState;
import com.codinglair.taf.runtime.definition.PayloadReference;
import com.codinglair.taf.runtime.definition.RepositoryAuthority;
import com.codinglair.taf.runtime.definition.RepositoryConflictException;
import com.codinglair.taf.runtime.definition.SecretReference;
import com.codinglair.taf.runtime.definition.TestDefinition;
import com.codinglair.taf.runtime.definition.TestDefinitionRepository;
import com.codinglair.taf.runtime.definition.VersionedTestDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bson.Document;

/** MongoDB implementation of the versioned definition repository for one isolated project. */
public final class MongoTestDefinitionRepository implements TestDefinitionRepository {
  public static final String VERSION_INDEX = "taf_project_case_version_uq";
  public static final String LATEST_INDEX = "taf_project_case_latest";

  private final MongoCollection<Document> collection;
  private final ObjectMapper mapper;
  private final String project;
  private final RepositoryAuthority authority;
  private final int schemaVersion;

  public MongoTestDefinitionRepository(
      MongoDatabase database, MongoTestDefinitionProperties properties, ObjectMapper mapper) {
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(properties, "properties");
    this.collection = database.getCollection(properties.getCollection());
    this.mapper = Objects.requireNonNull(mapper, "mapper").copy();
    this.project = properties.getProject();
    this.authority = new RepositoryAuthority(properties.getAuthority());
    this.schemaVersion = properties.getSchemaVersion();
    if (properties.isBootstrapIndexes()) ensureApprovedIndexes();
    validateSchema();
  }

  public String project() {
    return project;
  }

  public void ensureApprovedIndexes() {
    collection.createIndex(
        compoundIndex(ascending("project"), ascending("caseId"), ascending("version")),
        new IndexOptions().name(VERSION_INDEX).unique(true));
    collection.createIndex(
        compoundIndex(ascending("project"), ascending("caseId"), descending("version")),
        new IndexOptions().name(LATEST_INDEX));
  }

  /** Returns the actual index names for operational validation without exposing the collection. */
  public List<String> indexNames() {
    List<String> result = new ArrayList<>();
    collection.listIndexes().map(document -> document.getString("name")).into(result);
    return List.copyOf(result);
  }

  public void validateSchema() {
    Document drift =
        collection.find(and(eq("project", project), ne("schemaVersion", schemaVersion))).first();
    if (drift != null) {
      throw new MongoDefinitionSchemaException(
          "TAF context project '"
              + project
              + "' requires an explicit migration to schema "
              + schemaVersion);
    }
  }

  @Override
  public <I, E> TestDefinition<I, E> require(
      String caseId, Class<I> inputType, Class<E> expectedOutputType) {
    Document stored = latest(caseId);
    if (stored == null) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.MISSING,
          caseId,
          "No test definition exists for case '" + normalize(caseId, "caseId") + "'");
    }
    return materialize(stored, inputType, expectedOutputType).definition();
  }

  @Override
  public <I, E> VersionedTestDefinition<I, E> save(
      VersionedTestDefinition<I, E> definition,
      long expectedLatestVersion,
      RepositoryAuthority suppliedAuthority) {
    Objects.requireNonNull(definition, "definition");
    verifyAuthority(suppliedAuthority);
    Document current = latest(definition.caseId());
    long actual = current == null ? 0 : number(current, "version");
    if (actual != expectedLatestVersion || definition.version() != expectedLatestVersion + 1) {
      throw versionConflict(definition.caseId(), expectedLatestVersion, actual);
    }
    try {
      collection.insertOne(toDocument(definition));
      return definition;
    } catch (MongoWriteException failure) {
      if (failure.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
        throw new RepositoryConflictException(
            RepositoryConflictException.Kind.CONCURRENT_WRITE,
            "A concurrent writer created definition '" + definition.caseId() + "'");
      }
      throw failure;
    }
  }

  @Override
  public <I, E> VersionedTestDefinition<I, E> transition(
      String caseId,
      DefinitionState target,
      long expectedLatestVersion,
      RepositoryAuthority suppliedAuthority,
      Class<I> inputType,
      Class<E> expectedOutputType) {
    VersionedTestDefinition<I, E> current =
        materialize(requiredLatest(caseId), inputType, expectedOutputType);
    if (current.version() != expectedLatestVersion) {
      throw versionConflict(caseId, expectedLatestVersion, current.version());
    }
    if (!current.state().canTransitionTo(Objects.requireNonNull(target, "target"))) {
      throw new IllegalStateException(
          "Definition state " + current.state() + " cannot transition to " + target);
    }
    TestDefinition<I, E> nextDefinition =
        new TestDefinition<>(
            current.caseId(),
            current.version() + 1,
            target,
            current.definition().input(),
            current.definition().expectedOutput(),
            current.definition().secretReferences());
    return save(
        new VersionedTestDefinition<>(
            nextDefinition, current.correlations(), current.payloadReferences()),
        expectedLatestVersion,
        suppliedAuthority);
  }

  @Override
  public <I, E> List<VersionedTestDefinition<I, E>> findByCorrelation(
      String name, String value, Class<I> inputType, Class<E> expectedOutputType) {
    String key = normalize(name, "name");
    String expected = normalize(value, "value");
    Map<String, Document> latestByCase = new LinkedHashMap<>();
    collection
        .find(and(eq("project", project), eq("correlations." + key, expected)))
        .sort(compoundIndex(ascending("caseId"), descending("version")))
        .forEach(document -> latestByCase.putIfAbsent(document.getString("caseId"), document));
    return latestByCase.values().stream()
        .map(document -> materialize(document, inputType, expectedOutputType))
        .toList();
  }

  List<Document> allDocuments() {
    List<Document> result = new ArrayList<>();
    collection
        .find(eq("project", project))
        .sort(compoundIndex(ascending("caseId"), ascending("version")))
        .into(result);
    return List.copyOf(result);
  }

  void importDocuments(List<Document> documents, RepositoryAuthority suppliedAuthority) {
    verifyAuthority(suppliedAuthority);
    for (Document source :
        documents.stream()
            .sorted(
                Comparator.comparing((Document value) -> value.getString("caseId"))
                    .thenComparingLong(value -> number(value, "version")))
            .toList()) {
      if (!project.equals(source.getString("project"))) {
        throw new RepositoryConflictException(
            RepositoryConflictException.Kind.AUTHORITY,
            "Snapshot project does not match the isolated repository namespace");
      }
      Document existing =
          collection
              .find(
                  and(
                      eq("project", project),
                      eq("caseId", source.getString("caseId")),
                      eq("version", number(source, "version"))))
              .first();
      if (existing != null) {
        Document comparable = new Document(source);
        comparable.remove("_id");
        Document persisted = new Document(existing);
        persisted.remove("_id");
        if (!persisted.equals(comparable)) {
          throw new RepositoryConflictException(
              RepositoryConflictException.Kind.CONCURRENT_WRITE,
              "Snapshot conflicts with an existing immutable definition version");
        }
        continue;
      }
      long expected = number(source, "version") - 1;
      Document latest = latest(source.getString("caseId"));
      long actual = latest == null ? 0 : number(latest, "version");
      if (actual != expected) throw versionConflict(source.getString("caseId"), expected, actual);
      Document imported = new Document(source);
      imported.remove("_id");
      collection.insertOne(imported);
    }
  }

  private Document requiredLatest(String caseId) {
    Document result = latest(caseId);
    if (result == null) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.MISSING,
          caseId,
          "No test definition exists for case '" + normalize(caseId, "caseId") + "'");
    }
    return result;
  }

  private Document latest(String caseId) {
    return collection
        .find(and(eq("project", project), eq("caseId", normalize(caseId, "caseId"))))
        .sort(descending("version"))
        .first();
  }

  private <I, E> Document toDocument(VersionedTestDefinition<I, E> value) {
    TestDefinition<I, E> definition = value.definition();
    return new Document("schemaVersion", schemaVersion)
        .append("project", project)
        .append("caseId", definition.caseId())
        .append("version", definition.version())
        .append("state", definition.state().name())
        .append("input", encode(definition.input()))
        .append("expectedOutput", encode(definition.expectedOutput()))
        .append("secretReferences", encode(definition.secretReferences()))
        .append("correlations", encode(value.correlations()))
        .append("payloadReferences", encode(value.payloadReferences()));
  }

  private Object encode(Object value) {
    try {
      return Document.parse(mapper.writeValueAsString(Map.of("value", value))).get("value");
    } catch (JsonProcessingException failure) {
      throw new IllegalArgumentException("Definition cannot be serialized", failure);
    }
  }

  private JsonNode decodeNode(Object value) {
    return mapper.valueToTree(value);
  }

  private <I, E> VersionedTestDefinition<I, E> materialize(
      Document stored, Class<I> inputType, Class<E> expectedOutputType) {
    try {
      I input = mapper.treeToValue(decodeNode(stored.get("input")), inputType);
      E expected = mapper.treeToValue(decodeNode(stored.get("expectedOutput")), expectedOutputType);
      Map<String, SecretReference> secrets =
          mapper.convertValue(
              stored.get("secretReferences"),
              mapper
                  .getTypeFactory()
                  .constructMapType(Map.class, String.class, SecretReference.class));
      Map<String, String> correlations =
          mapper.convertValue(
              stored.get("correlations"),
              mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
      List<PayloadReference> payloads =
          mapper.convertValue(
              stored.get("payloadReferences"),
              mapper.getTypeFactory().constructCollectionType(List.class, PayloadReference.class));
      TestDefinition<I, E> definition =
          new TestDefinition<>(
              stored.getString("caseId"),
              number(stored, "version"),
              DefinitionState.valueOf(stored.getString("state")),
              input,
              expected,
              secrets);
      return new VersionedTestDefinition<>(definition, correlations, payloads);
    } catch (JsonProcessingException | IllegalArgumentException failure) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.TYPE_CONVERSION,
          stored.getString("caseId"),
          "Test case '" + stored.getString("caseId") + "' cannot be converted to requested types",
          failure);
    }
  }

  private void verifyAuthority(RepositoryAuthority supplied) {
    if (!authority.equals(Objects.requireNonNull(supplied, "authority"))) {
      throw new RepositoryConflictException(
          RepositoryConflictException.Kind.AUTHORITY,
          "Write rejected because the configured source is not authoritative");
    }
  }

  private static RepositoryConflictException versionConflict(
      String id, long expected, long actual) {
    return new RepositoryConflictException(
        RepositoryConflictException.Kind.VERSION,
        "Definition '" + id + "' expected latest version " + expected + " but was " + actual);
  }

  static long number(Document document, String name) {
    Object value = document.get(name);
    if (value instanceof Number number) return number.longValue();
    if (value instanceof String string) {
      try {
        return Long.parseLong(string);
      } catch (NumberFormatException failure) {
        throw new IllegalArgumentException(name + " must be numeric", failure);
      }
    }
    if (value == null) throw new IllegalArgumentException(name + " is missing");
    throw new IllegalArgumentException(
        name + " must be numeric, got " + value.getClass().getName());
  }

  static String normalize(String value, String name) {
    String result = Objects.requireNonNull(value, name).trim();
    if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    if (result.length() > 128)
      throw new IllegalArgumentException(name + " must not exceed 128 characters");
    if (result.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)))
      throw new IllegalArgumentException(name + " contains control characters");
    if (result.contains("$") || result.contains("."))
      throw new IllegalArgumentException(name + " contains unsupported MongoDB key characters");
    return result;
  }
}
