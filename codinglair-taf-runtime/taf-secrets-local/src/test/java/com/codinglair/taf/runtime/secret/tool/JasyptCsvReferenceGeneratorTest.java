package com.codinglair.taf.runtime.secret.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.secret.JasyptSecretProvider;
import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JasyptCsvReferenceGeneratorTest {
  @TempDir Path temporary;

  @Test
  void createsDistinctDecryptableReferencesWithoutPersistingPlaintextOrKey() throws Exception {
    String key = UUID.randomUUID().toString();
    String plaintext = "canary-" + UUID.randomUUID();
    Map<String, String> environment = Map.of("MASTER_KEY", key, "USER_PASSWORD", plaintext);
    Path input = temporary.resolve("inputs.csv");
    Path output = temporary.resolve("generated.csv");
    Files.write(
        input,
        List.of(
            "id,passwordReference",
            "one,secret://jasypt/GENERATE_WITH_LOCAL_UTILITY_0001",
            "two,secret://jasypt/GENERATE_WITH_LOCAL_UTILITY_0002"));

    assertThat(
            JasyptCsvReferenceGenerator.generate(
                input, output, "MASTER_KEY", "USER_PASSWORD", environment::get))
        .isEqualTo(2);
    String generated = Files.readString(output);
    assertThat(generated).doesNotContain(plaintext, key, "GENERATE_WITH_LOCAL_UTILITY");
    List<String> references =
        Files.readAllLines(output).stream()
            .skip(1)
            .map(line -> line.substring(line.indexOf("secret://")))
            .toList();
    assertThat(references).doesNotHaveDuplicates();
    JasyptSecretProvider provider = new JasyptSecretProvider(environment::get, "MASTER_KEY");
    for (String reference : references) {
      try (ResolvedSecret resolved = provider.resolve(SecretReference.parse(reference))) {
        assertThat(resolved.useAsString()).isEqualTo(plaintext);
      }
    }
  }

  @Test
  void refusesInPlaceAndInsufficientPersonaInput() throws Exception {
    Path input = temporary.resolve("inputs.csv");
    Files.writeString(input, "id,passwordReference\none,secret://env/USER_PASSWORD\n");
    assertThatThrownBy(
            () ->
                JasyptCsvReferenceGenerator.generate(
                    input, input, "MASTER_KEY", "USER_PASSWORD", name -> "value"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                JasyptCsvReferenceGenerator.generate(
                    input,
                    temporary.resolve("output.csv"),
                    "MASTER_KEY",
                    "USER_PASSWORD",
                    name -> "value"))
        .isInstanceOf(IllegalStateException.class);
  }
}
