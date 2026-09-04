package com.codinglair.taf.runtime.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecretReferenceTest {
  @Test
  void parsesCanonicalFormsWithoutDisclosingPayload() {
    for (String raw :
        new String[] {
          "secret://env/TEST_PASSWORD",
          "secret://jasypt/ABCDEFGHIJKLMNOP",
          "credential://saucedemo/standard-user"
        }) {
      SecretReference reference = SecretReference.parse(raw);
      assertThat(reference.toString())
          .doesNotContain(raw)
          .doesNotContain(reference.providerPayload());
      assertThat(reference.correlationToken()).hasSize(16);
    }
  }

  @Test
  void rejectsMalformedUnknownOversizedAndInjectionReferences() {
    for (String raw :
        new String[] {
          "",
          "plaintext",
          "secret://vault/path",
          "secret://env/lower",
          "secret://env/${PASSWORD}",
          "secret://env/../PASSWORD",
          "secret://jasypt/short"
        }) {
      var assertion =
          assertThatThrownBy(() -> SecretReference.parse(raw))
              .isInstanceOf(IllegalArgumentException.class);
      if (!raw.isEmpty()) assertion.hasMessageNotContaining(raw);
    }
    assertThatThrownBy(
            () ->
                SecretReference.parse("secret://jasypt/" + "A".repeat(SecretReference.MAX_LENGTH)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvedHolderClearsAndNeverRendersValue() {
    ResolvedSecret secret = ResolvedSecret.of("canary-value".toCharArray());
    assertThat(secret.toString()).doesNotContain("canary-value");
    secret.close();
    assertThat(secret.isClosed()).isTrue();
    assertThatThrownBy(secret::useAsString).isInstanceOf(IllegalStateException.class);
  }
}
