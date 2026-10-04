package com.codinglair.taf.runtime.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CSV secret normalizer characterization")
class CsvSecretNormalizerCharacterizationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @DisplayName("preserves row field and CRLF ordering while closing transient plaintext")
  void preservesOrderingAndLineEndingsAndClosesTransientPlaintext() throws Exception {
    Path source =
        Files.writeString(
            temporaryDirectory.resolve("definitions.csv"),
            "caseId,note,passwordReference\r\nTC2,second,plain-two\r\nTC1,first,plain-one\r\n");
    var request =
        new CsvSecretNormalizationRequest(
            source,
            "caseId",
            Map.of("passwordReference", SecretFieldDefinition.required(SecretKind.PASSWORD)),
            "demo",
            "local",
            "test",
            true);
    AtomicReference<char[]> lastPlaintext = new AtomicReference<>();
    SecretProvisioner provisioner = provisioner(lastPlaintext);

    assertThat(new CsvSecretNormalizer().normalize(request, provisioner)).isEqualTo(2);
    assertThat(Files.readString(source))
        .isEqualTo(
            "caseId,note,passwordReference\r\n"
                + "TC2,second,\"secret://env/GENERATED_TC2\"\r\n"
                + "TC1,first,\"secret://env/GENERATED_TC1\"\r\n");
    assertThat(lastPlaintext.get()).containsOnly('\0');
  }

  private static SecretProvisioner provisioner(AtomicReference<char[]> lastPlaintext) {
    return new SecretProvisioner() {
      @Override
      public String id() {
        return "test";
      }

      @Override
      public void verifyReady() {}

      @Override
      public String protect(
          TransientSecretValue value, SecretKind kind, SecretProvisioningContext context) {
        value.use(
            plaintext -> {
              lastPlaintext.set(plaintext);
              return null;
            });
        return "secret://env/GENERATED_" + context.definitionId();
      }
    };
  }
}
