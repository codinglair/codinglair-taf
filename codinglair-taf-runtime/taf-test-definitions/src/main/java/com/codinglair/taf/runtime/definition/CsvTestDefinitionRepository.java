package com.codinglair.taf.runtime.definition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Eagerly validated, immutable repository joining paired CSV rows by case ID. */
public final class CsvTestDefinitionRepository implements TestDefinitionRepository {
  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
  private final CsvMapper mapper = new CsvMapper();
  private final Map<String, Map<String, String>> inputs;
  private final Map<String, Map<String, String>> expectedOutputs;

  public CsvTestDefinitionRepository(CsvTestDefinitionConfiguration configuration) {
    this(configuration, Thread.currentThread().getContextClassLoader());
  }

  CsvTestDefinitionRepository(
      CsvTestDefinitionConfiguration configuration, ClassLoader classLoader) {
    Objects.requireNonNull(configuration, "configuration");
    ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
    inputs =
        read(
            configuration.inputs(),
            configuration.caseIdColumn(),
            "inputs",
            configuration.inputSecretFields(),
            loader);
    expectedOutputs =
        read(
            configuration.expectedOutputs(),
            configuration.caseIdColumn(),
            "expected outputs",
            configuration.expectedOutputSecretFields(),
            loader);
  }

  @Override
  public <I, E> TestDefinition<I, E> require(
      String caseId, Class<I> inputType, Class<E> expectedOutputType) {
    String id = Objects.requireNonNull(caseId, "caseId").trim();
    Map<String, String> input = inputs.get(id);
    Map<String, String> expected = expectedOutputs.get(id);
    if (input == null || expected == null) {
      String missing =
          input == null && expected == null
              ? "inputs and expected outputs"
              : input == null ? "inputs" : "expected outputs";
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.MISSING,
          id,
          "Test case '" + id + "' is missing from " + missing);
    }
    validateSecrets(input, SecretSchemaIntrospector.inspect(inputType), id, "inputs");
    validateSecrets(
        expected, SecretSchemaIntrospector.inspect(expectedOutputType), id, "expected outputs");
    try {
      return new TestDefinition<>(
          id,
          1,
          DefinitionState.APPROVED,
          mapper.convertValue(input, Objects.requireNonNull(inputType, "inputType")),
          mapper.convertValue(
              expected, Objects.requireNonNull(expectedOutputType, "expectedOutputType")),
          Map.of());
    } catch (IllegalArgumentException failure) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.TYPE_CONVERSION,
          id,
          "Test case '"
              + id
              + "' cannot be converted to "
              + inputType.getName()
              + " and "
              + expectedOutputType.getName()
              + ": "
              + failure.getMessage(),
          failure);
    }
  }

  private Map<String, Map<String, String>> read(
      DefinitionResourceLocation location,
      String idColumn,
      String role,
      Map<String, SecretFieldDefinition> secretFields,
      ClassLoader classLoader) {
    try (InputStream stream = open(location, classLoader)) {
      var rows =
          mapper
              .readerFor(STRING_MAP)
              .with(CsvSchema.emptySchema().withHeader())
              .<Map<String, String>>readValues(stream);
      Map<String, Map<String, String>> indexed = new LinkedHashMap<>();
      while (rows.hasNext()) {
        Map<String, String> row = new LinkedHashMap<>(rows.next());
        String id = row.remove(idColumn);
        if (id == null || id.isBlank()) {
          throw new DefinitionDiagnosticException(
              DefinitionDiagnosticException.Kind.MALFORMED,
              null,
              "CSV " + role + " contains a row with missing column '" + idColumn + "'");
        }
        id = id.trim();
        validateSecrets(row, secretFields, id, role);
        if (indexed.putIfAbsent(id, Map.copyOf(row)) != null) {
          throw new DefinitionDiagnosticException(
              DefinitionDiagnosticException.Kind.DUPLICATE,
              id,
              "CSV " + role + " contains duplicate test case ID '" + id + "'");
        }
      }
      return Map.copyOf(indexed);
    } catch (DefinitionDiagnosticException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new DefinitionDiagnosticException(
          DefinitionDiagnosticException.Kind.MALFORMED,
          null,
          "Cannot read CSV "
              + role
              + " from "
              + location.kind()
              + " location '"
              + location.value()
              + "'",
          failure);
    }
  }

  private static void validateSecrets(
      Map<String, String> row,
      Map<String, SecretFieldDefinition> secretFields,
      String id,
      String role) {
    for (Map.Entry<String, SecretFieldDefinition> classified : secretFields.entrySet()) {
      String value = row.get(classified.getKey());
      if (value == null || value.isBlank()) {
        if (classified.getValue().required()) {
          throw secretFailure(id, role, classified.getKey(), "is required");
        }
        continue;
      }
      try {
        SecretReference.requireApproved(value);
      } catch (IllegalArgumentException failure) {
        throw secretFailure(id, role, classified.getKey(), "is not an approved secret reference");
      }
    }
  }

  private static DefinitionDiagnosticException secretFailure(
      String id, String role, String field, String reason) {
    return new DefinitionDiagnosticException(
        DefinitionDiagnosticException.Kind.SECRET_INGESTION,
        id,
        "CSV " + role + " test case '" + id + "' classified field '" + field + "' " + reason);
  }

  private static InputStream open(DefinitionResourceLocation location, ClassLoader classLoader)
      throws IOException {
    if (location.kind() == DefinitionResourceLocation.Kind.CLASSPATH) {
      InputStream stream = classLoader.getResourceAsStream(location.value());
      if (stream == null)
        throw new IOException("Classpath resource not found: " + location.value());
      return stream;
    }
    Path path = Path.of(location.value()).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) throw new IOException("External file not found: " + path);
    return Files.newInputStream(path);
  }
}
