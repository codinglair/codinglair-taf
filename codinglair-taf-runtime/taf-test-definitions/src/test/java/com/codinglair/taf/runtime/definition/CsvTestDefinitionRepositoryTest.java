package com.codinglair.taf.runtime.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CsvTestDefinitionRepositoryTest {
  @Test
  void classifiedPlaintextIsRejectedWithoutEchoingOrMutation() throws Exception {
    String canary = "runtime-canary-" + java.util.UUID.randomUUID();
    Path inputs = write("classified.csv", "caseId,passwordReference\nTC1," + canary + "\n");
    Path expected = write("expected.csv", "caseId,outcome,count\nTC1,ok,1\n");
    String original = Files.readString(inputs);
    var configuration =
        new CsvTestDefinitionConfiguration(
            DefinitionResourceLocation.file(inputs),
            DefinitionResourceLocation.file(expected),
            "caseId",
            Map.of("passwordReference", SecretFieldDefinition.required(SecretKind.PASSWORD)),
            Map.of());

    assertThatThrownBy(() -> new CsvTestDefinitionRepository(configuration))
        .isInstanceOfSatisfying(
            DefinitionDiagnosticException.class,
            failure -> {
              assertThat(failure.kind())
                  .isEqualTo(DefinitionDiagnosticException.Kind.SECRET_INGESTION);
              assertThat(failure).hasMessageNotContaining(canary);
            });
    assertThat(Files.readString(inputs)).isEqualTo(original);
  }

  @Test
  void explicitNormalizationIsAtomicIdempotentAndRejectsUnauthorizedEnvironments()
      throws Exception {
    String first = "first-" + java.util.UUID.randomUUID();
    String second = "second-" + java.util.UUID.randomUUID();
    Path inputs =
        write(
            "normalize.csv",
            "caseId,passwordReference,note\r\nTC1," + first + ",one\r\nTC2," + second + ",two\r\n");
    var request =
        new CsvSecretNormalizationRequest(
            inputs,
            "caseId",
            Map.of("passwordReference", SecretFieldDefinition.required(SecretKind.PASSWORD)),
            "demo",
            "local",
            "test",
            true);
    SecretProvisioner provisioner =
        new SecretProvisioner() {
          private int sequence;

          public String id() {
            return "test";
          }

          public void verifyReady() {}

          public String protect(
              TransientSecretValue value, SecretKind kind, SecretProvisioningContext context) {
            return "secret://env/GENERATED_" + ++sequence;
          }
        };

    assertThat(new CsvSecretNormalizer().normalize(request, provisioner)).isEqualTo(2);
    String canonical = Files.readString(inputs);
    assertThat(canonical)
        .contains("secret://env/GENERATED_1", "secret://env/GENERATED_2", "one", "two")
        .doesNotContain(first, second);
    assertThat(new CsvSecretNormalizer().normalize(request, provisioner)).isZero();
    assertThat(Files.readString(inputs)).isEqualTo(canonical);

    var production =
        new CsvSecretNormalizationRequest(
            inputs, "caseId", request.secretFields(), "demo", "production", "test", true);
    assertThatThrownBy(() -> new CsvSecretNormalizer().normalize(production, provisioner))
        .isInstanceOf(SecretNormalizationException.class);
  }

  @Test
  void annotationClassificationIsExplicitAndDetectsConflicts() {
    assertThat(SecretSchemaIntrospector.inspect(AnnotatedInput.class))
        .containsEntry("reference", SecretFieldDefinition.required(SecretKind.API_KEY))
        .doesNotContainKey("passwordLookingButUnclassified");
    assertThatThrownBy(() -> SecretSchemaIntrospector.inspect(ConflictingInput.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvesOneTypedInputAndExpectedOutputFromClasspath() {
    var repository =
        new CsvTestDefinitionRepository(
            configuration(
                DefinitionResourceLocation.classpath("definitions/inputs.csv"),
                DefinitionResourceLocation.classpath("definitions/expected.csv")));

    TestDefinition<LoginInput, ProductExpectation> definition =
        new TestDefinitionResolver(repository)
            .require("TC0001", LoginInput.class, ProductExpectation.class);

    assertThat(definition.input()).isEqualTo(new LoginInput("standard_user", 2));
    assertThat(definition.expectedOutput()).isEqualTo(new ProductExpectation("success", 6));
    assertThat(definition.state()).isEqualTo(DefinitionState.APPROVED);
  }

  @Test
  void supportsExternalFilesWithoutMongoDb() throws Exception {
    Path inputs = write("inputs.csv", "caseId,username,retries\nTC9,user,1\n");
    Path expected = write("expected.csv", "caseId,outcome,count\nTC9,ok,3\n");
    var repository =
        new CsvTestDefinitionRepository(
            configuration(
                DefinitionResourceLocation.file(inputs),
                DefinitionResourceLocation.file(expected)));

    assertThat(repository.require("TC9", LoginInput.class, ProductExpectation.class).caseId())
        .isEqualTo("TC9");
  }

  @Test
  void duplicateIdsFailDuringRepositoryInitialization() throws Exception {
    Path inputs = write("duplicate.csv", "caseId,username,retries\nTC1,a,1\nTC1,b,2\n");
    Path expected = write("expected.csv", "caseId,outcome,count\nTC1,ok,1\n");
    assertThatThrownBy(
            () ->
                new CsvTestDefinitionRepository(
                    configuration(
                        DefinitionResourceLocation.file(inputs),
                        DefinitionResourceLocation.file(expected))))
        .isInstanceOfSatisfying(
            DefinitionDiagnosticException.class,
            failure ->
                assertThat(failure.kind()).isEqualTo(DefinitionDiagnosticException.Kind.DUPLICATE));
  }

  @Test
  void missingAndTypeConversionFailuresAreActionable() throws Exception {
    var repository =
        new CsvTestDefinitionRepository(
            configuration(
                DefinitionResourceLocation.classpath("definitions/inputs.csv"),
                DefinitionResourceLocation.classpath("definitions/expected.csv")));
    assertThatThrownBy(
            () -> repository.require("absent", LoginInput.class, ProductExpectation.class))
        .isInstanceOfSatisfying(
            DefinitionDiagnosticException.class,
            failure ->
                assertThat(failure.kind()).isEqualTo(DefinitionDiagnosticException.Kind.MISSING));

    Path inputs = write("bad.csv", "caseId,username,retries\nTC1,user,not-a-number\n");
    Path expected = write("expected.csv", "caseId,outcome,count\nTC1,ok,1\n");
    var malformed =
        new CsvTestDefinitionRepository(
            configuration(
                DefinitionResourceLocation.file(inputs),
                DefinitionResourceLocation.file(expected)));
    assertThatThrownBy(() -> malformed.require("TC1", LoginInput.class, ProductExpectation.class))
        .isInstanceOfSatisfying(
            DefinitionDiagnosticException.class,
            failure ->
                assertThat(failure.kind())
                    .isEqualTo(DefinitionDiagnosticException.Kind.TYPE_CONVERSION));
  }

  @Test
  void immutableContractsRejectInvalidVersionsAndUnsafeSecretAliases() {
    assertThatThrownBy(() -> new SecretReference("resolved secret value"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TestDefinition<>(
                    "TC1",
                    0,
                    DefinitionState.DRAFT,
                    new LoginInput("u", 1),
                    new ProductExpectation("ok", 1),
                    java.util.Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void concurrentLookupsRemainIsolatedAndDeterministic() {
    var repository =
        new CsvTestDefinitionRepository(
            configuration(
                DefinitionResourceLocation.classpath("definitions/inputs.csv"),
                DefinitionResourceLocation.classpath("definitions/expected.csv")));

    var resolved =
        IntStream.range(0, 100)
            .parallel()
            .mapToObj(
                ignored -> repository.require("TC0001", LoginInput.class, ProductExpectation.class))
            .toList();

    assertThat(resolved).hasSize(100).allMatch(definition -> definition.caseId().equals("TC0001"));
    assertThat(resolved)
        .allSatisfy(
            definition ->
                assertThat(definition.input()).isEqualTo(new LoginInput("standard_user", 2)));
  }

  private CsvTestDefinitionConfiguration configuration(
      DefinitionResourceLocation inputs, DefinitionResourceLocation expected) {
    return new CsvTestDefinitionConfiguration(inputs, expected, "caseId");
  }

  private Path write(String name, String content) throws Exception {
    Path directory = Path.of("target", "test-data", java.util.UUID.randomUUID().toString());
    Files.createDirectories(directory);
    return Files.writeString(directory.resolve(name), content);
  }

  record LoginInput(String username, int retries) {}

  record ProductExpectation(String outcome, int count) {}

  record AnnotatedInput(
      @SecretField(kind = SecretKind.API_KEY) String reference,
      String passwordLookingButUnclassified) {}

  static class ConflictingBase {
    @SecretField(kind = SecretKind.PASSWORD)
    String value;
  }

  static final class ConflictingInput extends ConflictingBase {
    @SecretField(kind = SecretKind.API_KEY)
    String value() {
      return value;
    }
  }
}
