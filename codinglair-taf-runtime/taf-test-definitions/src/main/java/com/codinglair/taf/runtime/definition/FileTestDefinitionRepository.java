package com.codinglair.taf.runtime.definition;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Thread-safe JSON/YAML provider that retains every immutable definition version. */
public final class FileTestDefinitionRepository implements TestDefinitionRepository {
  private static final int SCHEMA_VERSION = 1;
  private final FileTestDefinitionConfiguration configuration;
  private final ObjectMapper mapper;
  private final Object monitor = new Object();

  public FileTestDefinitionRepository(FileTestDefinitionConfiguration configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    mapper = createMapper(configuration.format());
    synchronized (monitor) {
      if (Files.exists(configuration.file())) readDocument();
    }
  }

  @Override
  public <I, E> TestDefinition<I, E> require(
      String caseId, Class<I> inputType, Class<E> expectedOutputType) {
    synchronized (monitor) {
      StoredDefinition stored = latest(readDocument(), normalize(caseId));
      return materialize(stored, inputType, expectedOutputType);
    }
  }

  @Override
  public <I, E> VersionedTestDefinition<I, E> save(
      VersionedTestDefinition<I, E> definition,
      long expectedLatestVersion,
      RepositoryAuthority authority) {
    Objects.requireNonNull(definition, "definition");
    verifyAuthority(authority);
    synchronized (monitor) {
      return withWriteLock(
          () -> {
            Document document = readDocument();
            long latest = latestVersion(document, definition.caseId());
            if (latest != expectedLatestVersion || definition.version() != latest + 1) {
              throw versionConflict(definition.caseId(), expectedLatestVersion, latest);
            }
            List<StoredDefinition> definitions = new ArrayList<>(document.definitions());
            definitions.add(store(definition));
            writeDocument(
                new Document(SCHEMA_VERSION, configuration.authority().id(), definitions));
            return definition;
          });
    }
  }

  @Override
  public <I, E> VersionedTestDefinition<I, E> transition(
      String caseId,
      DefinitionState target,
      long expectedLatestVersion,
      RepositoryAuthority authority,
      Class<I> inputType,
      Class<E> expectedOutputType) {
    verifyAuthority(authority);
    synchronized (monitor) {
      return withWriteLock(
          () -> {
            Document document = readDocument();
            StoredDefinition current = latest(document, normalize(caseId));
            if (current.version() != expectedLatestVersion) {
              throw versionConflict(caseId, expectedLatestVersion, current.version());
            }
            if (!current.state().canTransitionTo(Objects.requireNonNull(target, "target"))) {
              throw new IllegalStateException(
                  "Definition '"
                      + caseId
                      + "' cannot transition from "
                      + current.state()
                      + " to "
                      + target);
            }
            VersionedTestDefinition<I, E> materialized =
                materializeVersioned(current, inputType, expectedOutputType);
            var transitioned =
                new VersionedTestDefinition<>(
                    new TestDefinition<>(
                        materialized.caseId(),
                        materialized.version() + 1,
                        target,
                        materialized.definition().input(),
                        materialized.definition().expectedOutput(),
                        materialized.definition().secretReferences()),
                    materialized.correlations(),
                    materialized.payloadReferences());
            List<StoredDefinition> definitions = new ArrayList<>(document.definitions());
            definitions.add(store(transitioned));
            writeDocument(
                new Document(SCHEMA_VERSION, configuration.authority().id(), definitions));
            return transitioned;
          });
    }
  }

  private <T> T withWriteLock(WriteOperation<T> operation) {
    Path target = configuration.file();
    Path parent = target.getParent();
    if (parent == null)
      throw new IllegalArgumentException("Definition file requires a parent directory");
    Path lock = parent.resolve(target.getFileName() + ".lock");
    try {
      Files.createDirectories(parent);
      Files.createFile(lock);
    } catch (FileAlreadyExistsException failure) {
      throw new RepositoryConflictException(
          RepositoryConflictException.Kind.CONCURRENT_WRITE,
          "Definition document has another write in progress");
    } catch (IOException failure) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.MALFORMED,
          null,
          "Cannot acquire definition write lock",
          failure);
    }
    try {
      return operation.execute();
    } finally {
      try {
        Files.deleteIfExists(lock);
      } catch (IOException failure) {
        throw new DefinitionDiagnosticException(
            DefinitionDiagnosticException.Kind.MALFORMED,
            null,
            "Cannot release definition write lock",
            failure);
      }
    }
  }

  @Override
  public <I, E> List<VersionedTestDefinition<I, E>> findByCorrelation(
      String name, String value, Class<I> inputType, Class<E> expectedOutputType) {
    String key = normalize(name);
    String expected = normalize(value);
    synchronized (monitor) {
      Map<String, StoredDefinition> latest = new TreeMap<>();
      for (StoredDefinition definition : readDocument().definitions()) {
        latest.merge(
            definition.caseId(),
            definition,
            (left, right) -> left.version() > right.version() ? left : right);
      }
      return latest.values().stream()
          .filter(definition -> expected.equals(definition.correlations().get(key)))
          .map(definition -> materializeVersioned(definition, inputType, expectedOutputType))
          .toList();
    }
  }

  private Document readDocument() {
    Path file = configuration.file();
    if (!Files.exists(file))
      return new Document(SCHEMA_VERSION, configuration.authority().id(), List.of());
    try {
      Document document = mapper.readValue(file.toFile(), Document.class);
      if (document.schemaVersion() != SCHEMA_VERSION) {
        throw new IllegalArgumentException(
            "Unsupported definition schema version " + document.schemaVersion());
      }
      if (!configuration.authority().id().equals(document.authority())) {
        throw new RepositoryConflictException(
            RepositoryConflictException.Kind.AUTHORITY,
            "Definition document authority does not match configured authority");
      }
      validate(document);
      return document;
    } catch (RepositoryConflictException | DefinitionDiagnosticException failure) {
      throw failure;
    } catch (IOException | IllegalArgumentException failure) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.MALFORMED,
          null,
          "Cannot read definition document '" + file + "'",
          failure);
    }
  }

  private void writeDocument(Document document) {
    Path target = configuration.file();
    try {
      Path parent = target.getParent();
      if (parent == null) throw new IOException("Definition file requires a parent directory");
      Files.createDirectories(parent);
      Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
      try {
        mapper.writeValue(temporary.toFile(), canonical(document));
        try {
          Files.move(
              temporary,
              target,
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
          Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException failure) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.MALFORMED,
          null,
          "Cannot atomically write definition document '" + target + "'",
          failure);
    }
  }

  private Document canonical(Document document) {
    List<StoredDefinition> sorted =
        document.definitions().stream()
            .sorted(
                Comparator.comparing(StoredDefinition::caseId)
                    .thenComparingLong(StoredDefinition::version))
            .toList();
    return new Document(document.schemaVersion(), document.authority(), sorted);
  }

  private static void validate(Document document) {
    Map<String, Long> versions = new LinkedHashMap<>();
    for (StoredDefinition definition : document.definitions()) {
      long previous = versions.getOrDefault(definition.caseId(), 0L);
      if (definition.version() != previous + 1) {
        throw new DefinitionDiagnosticException(
            DefinitionDiagnosticException.Kind.MALFORMED,
            definition.caseId(),
            "Definition versions must be contiguous and unique for '" + definition.caseId() + "'");
      }
      versions.put(definition.caseId(), definition.version());
      for (SecretReference reference : definition.secretReferences().values()) {
        SecretReference.requireApproved(reference.alias());
      }
    }
  }

  private StoredDefinition latest(Document document, String caseId) {
    return document.definitions().stream()
        .filter(definition -> definition.caseId().equals(caseId))
        .max(Comparator.comparingLong(StoredDefinition::version))
        .orElseThrow(
            () ->
                new DefinitionDiagnosticException(
                    DefinitionDiagnosticException.Kind.MISSING,
                    caseId,
                    "Test case '" + caseId + "' is missing"));
  }

  private long latestVersion(Document document, String caseId) {
    return document.definitions().stream()
        .filter(definition -> definition.caseId().equals(caseId))
        .mapToLong(StoredDefinition::version)
        .max()
        .orElse(0);
  }

  private StoredDefinition store(VersionedTestDefinition<?, ?> definition) {
    return new StoredDefinition(
        definition.caseId(),
        definition.version(),
        definition.state(),
        mapper.valueToTree(definition.definition().input()),
        mapper.valueToTree(definition.definition().expectedOutput()),
        new TreeMap<>(definition.definition().secretReferences()),
        new TreeMap<>(definition.correlations()),
        definition.payloadReferences());
  }

  private <I, E> TestDefinition<I, E> materialize(
      StoredDefinition stored, Class<I> inputType, Class<E> expectedOutputType) {
    try {
      return new TestDefinition<>(
          stored.caseId(),
          stored.version(),
          stored.state(),
          mapper.treeToValue(stored.input(), Objects.requireNonNull(inputType, "inputType")),
          mapper.treeToValue(
              stored.expectedOutput(),
              Objects.requireNonNull(expectedOutputType, "expectedOutputType")),
          stored.secretReferences());
    } catch (IOException | IllegalArgumentException failure) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.TYPE_CONVERSION,
          stored.caseId(),
          "Test case '" + stored.caseId() + "' cannot be converted to requested types",
          failure);
    }
  }

  private <I, E> VersionedTestDefinition<I, E> materializeVersioned(
      StoredDefinition stored, Class<I> inputType, Class<E> expectedOutputType) {
    return new VersionedTestDefinition<>(
        materialize(stored, inputType, expectedOutputType),
        stored.correlations(),
        stored.payloadReferences());
  }

  private void verifyAuthority(RepositoryAuthority supplied) {
    if (!configuration.authority().equals(Objects.requireNonNull(supplied, "authority"))) {
      throw new RepositoryConflictException(
          RepositoryConflictException.Kind.AUTHORITY,
          "Write rejected because the provider is not authoritative");
    }
  }

  private static RepositoryConflictException versionConflict(
      String id, long expected, long actual) {
    return new RepositoryConflictException(
        RepositoryConflictException.Kind.VERSION,
        "Definition '" + id + "' expected latest version " + expected + " but was " + actual);
  }

  private static String normalize(String value) {
    String result = Objects.requireNonNull(value, "value").trim();
    if (result.isEmpty()) throw new IllegalArgumentException("value must not be blank");
    return result;
  }

  private static ObjectMapper createMapper(DefinitionFileFormat format) {
    ObjectMapper result =
        switch (format) {
          case JSON -> new ObjectMapper();
          case YAML ->
              new ObjectMapper(
                  YAMLFactory.builder()
                      .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                      .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                      .build());
        };
    result.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    result.enable(SerializationFeature.INDENT_OUTPUT);
    return result;
  }

  @JsonPropertyOrder({"schemaVersion", "authority", "definitions"})
  private record Document(int schemaVersion, String authority, List<StoredDefinition> definitions) {
    private Document {
      authority = Objects.requireNonNull(authority, "authority");
      definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
    }
  }

  @JsonPropertyOrder({
    "caseId",
    "version",
    "state",
    "input",
    "expectedOutput",
    "secretReferences",
    "correlations",
    "payloadReferences"
  })
  private record StoredDefinition(
      String caseId,
      long version,
      DefinitionState state,
      JsonNode input,
      JsonNode expectedOutput,
      Map<String, SecretReference> secretReferences,
      Map<String, String> correlations,
      List<PayloadReference> payloadReferences) {
    private StoredDefinition {
      caseId = normalize(caseId);
      if (version < 1) throw new IllegalArgumentException("version must be positive");
      state = Objects.requireNonNull(state, "state");
      input = Objects.requireNonNull(input, "input");
      expectedOutput = Objects.requireNonNull(expectedOutput, "expectedOutput");
      secretReferences = Map.copyOf(Objects.requireNonNull(secretReferences, "secretReferences"));
      correlations = Map.copyOf(Objects.requireNonNull(correlations, "correlations"));
      payloadReferences =
          List.copyOf(Objects.requireNonNull(payloadReferences, "payloadReferences"));
    }
  }

  @FunctionalInterface
  private interface WriteOperation<T> {
    T execute();
  }
}
