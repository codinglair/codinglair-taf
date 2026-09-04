package com.codinglair.taf.runtime.definition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit, authorized CSV normalization service with verified atomic replacement. */
public final class CsvSecretNormalizer {
  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
  private final CsvMapper mapper = new CsvMapper();

  public int normalize(CsvSecretNormalizationRequest request, SecretProvisioner provisioner) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(provisioner, "provisioner");
    authorize(request);
    provisioner.verifyReady();
    Path source = request.source();
    if (!Files.isRegularFile(source) || !Files.isWritable(source)) {
      throw failure("CSV source is unavailable or read-only", null);
    }
    try {
      byte[] original = Files.readAllBytes(source);
      byte[] version = digest(original);
      String lineEnding =
          new String(original, StandardCharsets.UTF_8).contains("\r\n") ? "\r\n" : "\n";
      CsvSchema schema = CsvSchema.emptySchema().withHeader();
      var iterator =
          mapper.readerFor(STRING_MAP).with(schema).<Map<String, String>>readValues(original);
      List<Map<String, String>> rows = new ArrayList<>();
      int normalized = 0;
      while (iterator.hasNext()) {
        Map<String, String> row = new LinkedHashMap<>(iterator.next());
        String id = row.get(request.caseIdColumn());
        if (id == null || id.isBlank())
          throw failure("CSV row has no safe definition identifier", null);
        for (Map.Entry<String, SecretFieldDefinition> field : request.secretFields().entrySet()) {
          String value = row.get(field.getKey());
          if (value == null || value.isBlank()) {
            if (field.getValue().required())
              throw failure("A required classified field is absent", null);
            continue;
          }
          if (isApproved(value)) continue;
          if (value.startsWith("secret://")
              || value.startsWith("credential://")
              || value.startsWith("ENC(")) {
            throw failure("A classified field contains a malformed or unsupported reference", null);
          }
          SecretProvisioningContext context =
              new SecretProvisioningContext(
                  request.project(),
                  request.environment(),
                  source.getFileName().toString(),
                  id,
                  field.getKey(),
                  request.caller(),
                  request.authorized());
          try (TransientSecretValue plaintext = TransientSecretValue.of(value.toCharArray())) {
            String protectedReference =
                provisioner.protect(plaintext, field.getValue().kind(), context);
            SecretReference.requireApproved(protectedReference);
            row.put(field.getKey(), protectedReference);
            normalized++;
          }
        }
        rows.add(row);
      }
      if (normalized == 0) return 0;
      byte[] canonical = write(rows, lineEnding);
      validateCanonical(canonical, request);
      if (!MessageDigest.isEqual(version, digest(Files.readAllBytes(source)))) {
        throw failure("CSV source changed concurrently", null);
      }
      atomicReplace(source, canonical);
      validateCanonical(Files.readAllBytes(source), request);
      return normalized;
    } catch (SecretNormalizationException failure) {
      throw failure;
    } catch (IOException failure) {
      throw failure("CSV normalization failed safely", failure);
    }
  }

  private byte[] write(List<Map<String, String>> rows, String lineEnding) throws IOException {
    CsvSchema schema =
        CsvSchema.builder()
            .addColumns(rows.getFirst().keySet(), CsvSchema.ColumnType.STRING)
            .setUseHeader(true)
            .setLineSeparator(lineEnding)
            .build();
    return mapper.writer(schema).writeValueAsBytes(rows);
  }

  private void validateCanonical(byte[] content, CsvSecretNormalizationRequest request)
      throws IOException {
    var rows =
        mapper
            .readerFor(STRING_MAP)
            .with(CsvSchema.emptySchema().withHeader())
            .<Map<String, String>>readValues(content);
    while (rows.hasNext()) {
      Map<String, String> row = rows.next();
      for (Map.Entry<String, SecretFieldDefinition> field : request.secretFields().entrySet()) {
        String value = row.get(field.getKey());
        if ((value == null || value.isBlank()) && !field.getValue().required()) continue;
        SecretReference.requireApproved(value);
      }
    }
  }

  private static void atomicReplace(Path source, byte[] canonical) throws IOException {
    Path temporary = Files.createTempFile(source.getParent(), ".taf-secret-", ".tmp");
    try {
      Files.write(temporary, canonical);
      try {
        Files.move(
            temporary, source, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException failure) {
        throw failure;
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void authorize(CsvSecretNormalizationRequest request) {
    if (!request.authorized()) throw failure("CSV normalization is not authorized", null);
    if (!request.environment().equals("local")
        && !request.environment().startsWith("approved-staging")) {
      throw failure("CSV normalization is restricted to local or approved staging", null);
    }
  }

  private static boolean isApproved(String value) {
    try {
      SecretReference.requireApproved(value);
      return true;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static byte[] digest(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static SecretNormalizationException failure(String message, Throwable cause) {
    return new SecretNormalizationException(message, cause);
  }
}
