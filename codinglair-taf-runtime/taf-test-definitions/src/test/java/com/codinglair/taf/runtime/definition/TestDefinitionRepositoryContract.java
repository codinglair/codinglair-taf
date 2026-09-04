package com.codinglair.taf.runtime.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Reusable behavioral contract for writable versioned repository providers. */
public abstract class TestDefinitionRepositoryContract {
  protected static final RepositoryAuthority AUTHORITY = new RepositoryAuthority("git");

  protected abstract TestDefinitionRepository repository(Path file);

  protected abstract String extension();

  @Test
  void storesImmutableVersionsAndEnforcesLifecycle() throws Exception {
    TestDefinitionRepository repository = repository(file());
    VersionedTestDefinition<Input, Expected> draft = definition("TC-1", 1, DefinitionState.DRAFT);

    assertThat(repository.save(draft, 0, AUTHORITY)).isEqualTo(draft);
    assertThat(
            repository
                .transition(
                    "TC-1", DefinitionState.REVIEW, 1, AUTHORITY, Input.class, Expected.class)
                .version())
        .isEqualTo(2);
    assertThat(
            repository
                .transition(
                    "TC-1", DefinitionState.APPROVED, 2, AUTHORITY, Input.class, Expected.class)
                .version())
        .isEqualTo(3);
    assertThatThrownBy(
            () ->
                repository.transition(
                    "TC-1", DefinitionState.DRAFT, 3, AUTHORITY, Input.class, Expected.class))
        .isInstanceOf(IllegalStateException.class);
    assertThat(repository.require("TC-1", Input.class, Expected.class).state())
        .isEqualTo(DefinitionState.APPROVED);
  }

  @Test
  void reportsVersionAndAuthorityConflictsWithoutOverwriting() throws Exception {
    TestDefinitionRepository repository = repository(file());
    repository.save(definition("TC-1", 1, DefinitionState.DRAFT), 0, AUTHORITY);

    assertThatThrownBy(
            () -> repository.save(definition("TC-1", 2, DefinitionState.REVIEW), 0, AUTHORITY))
        .isInstanceOfSatisfying(
            RepositoryConflictException.class,
            failure ->
                assertThat(failure.kind()).isEqualTo(RepositoryConflictException.Kind.VERSION));
    assertThatThrownBy(
            () ->
                repository.save(
                    definition("TC-2", 1, DefinitionState.DRAFT),
                    0,
                    new RepositoryAuthority("mongodb")))
        .isInstanceOfSatisfying(
            RepositoryConflictException.class,
            failure ->
                assertThat(failure.kind()).isEqualTo(RepositoryConflictException.Kind.AUTHORITY));
    assertThat(repository.require("TC-1", Input.class, Expected.class).version()).isOne();
  }

  @Test
  void concurrentCreatesAllowExactlyOneImmutableVersion() throws Exception {
    Path file = file();
    var outcomes = new ConcurrentLinkedQueue<String>();
    IntStream.range(0, 20)
        .parallel()
        .forEach(
            index -> {
              try {
                repository(file)
                    .save(definitionUnchecked("TC-1", 1, DefinitionState.DRAFT), 0, AUTHORITY);
                outcomes.add("saved");
              } catch (RepositoryConflictException failure) {
                outcomes.add(failure.kind().name());
              }
            });

    assertThat(outcomes).filteredOn("saved"::equals).hasSize(1);
    assertThat(repository(file).require("TC-1", Input.class, Expected.class).version()).isOne();
  }

  @Test
  void correlationLookupIsExactAndDeterministicallyOrdered() throws Exception {
    TestDefinitionRepository repository = repository(file());
    repository.save(definition("TC-2", 1, DefinitionState.DRAFT), 0, AUTHORITY);
    repository.save(definition("TC-1", 1, DefinitionState.DRAFT), 0, AUTHORITY);

    assertThat(repository.findByCorrelation("trace", "order-7", Input.class, Expected.class))
        .extracting(VersionedTestDefinition::caseId)
        .containsExactly("TC-1", "TC-2");
  }

  @Test
  void roundTripIsStableAndPayloadMetadataDoesNotInterpretBytes() throws Exception {
    Path repositoryFile = file();
    TestDefinitionRepository repository = repository(repositoryFile);
    VersionedTestDefinition<Input, Expected> definition =
        definition("TC-1", 1, DefinitionState.DRAFT);
    repository.save(definition, 0, AUTHORITY);

    TestDefinition<Input, Expected> loaded =
        repository(repositoryFile).require("TC-1", Input.class, Expected.class);
    assertThat(loaded).isEqualTo(definition.definition());
    assertStableRoundTrip(repositoryFile, repository, definition);

    Path payload = repositoryFile.resolveSibling("sample.pdf");
    byte[] bytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0, (byte) 0xff};
    Files.write(payload, bytes);
    assertThat(Files.readAllBytes(payload)).containsExactly(bytes);
    assertThat(definition.payloadReferences().getFirst().mediaType()).isEqualTo("application/pdf");
  }

  /** Provider-specific deterministic persistence assertion used by every repository adapter. */
  protected abstract void assertStableRoundTrip(
      Path repositoryFile,
      TestDefinitionRepository repository,
      VersionedTestDefinition<Input, Expected> definition)
      throws Exception;

  /** Shared deterministic persistence assertion for file-backed adapters. */
  protected final void assertStableFileRoundTrip(
      Path repositoryFile,
      TestDefinitionRepository repository,
      VersionedTestDefinition<Input, Expected> definition)
      throws Exception {
    String first = Files.readString(repositoryFile);
    Path secondFile = repositoryFile.resolveSibling("second." + extension());
    repository(secondFile).save(definition, 0, AUTHORITY);
    assertThat(Files.readAllBytes(secondFile)).containsExactly(Files.readAllBytes(repositoryFile));
    repository(repositoryFile)
        .transition("TC-1", DefinitionState.REVIEW, 1, AUTHORITY, Input.class, Expected.class);
    assertThat(Files.readString(repositoryFile))
        .startsWith(first.substring(0, Math.min(20, first.length())));
  }

  protected VersionedTestDefinition<Input, Expected> definition(
      String id, long version, DefinitionState state) throws Exception {
    byte[] payload = new byte[] {0x25, 0x50, 0x44, 0x46, 0, (byte) 0xff};
    String checksum =
        "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    return new VersionedTestDefinition<>(
        new TestDefinition<>(
            id,
            version,
            state,
            new Input(Map.of("channel", Map.of("value", 7))),
            new Expected(Map.of("status", "ok")),
            Map.of("credential", SecretReference.requireApproved("secret://env/TEST_PASSWORD"))),
        Map.of("trace", "order-7"),
        java.util.List.of(
            new PayloadReference(
                "invoice",
                URI.create("file:payloads/sample.pdf"),
                checksum,
                "application/pdf",
                payload.length,
                "git:abc123")));
  }

  private VersionedTestDefinition<Input, Expected> definitionUnchecked(
      String id, long version, DefinitionState state) {
    try {
      return definition(id, version, state);
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private Path file() throws Exception {
    Path directory =
        Path.of("target", "repository-contract", java.util.UUID.randomUUID().toString());
    Files.createDirectories(directory);
    return directory.resolve("definitions." + extension());
  }

  protected record Input(Map<String, Map<String, Integer>> channels) {}

  protected record Expected(Map<String, String> values) {}
}
