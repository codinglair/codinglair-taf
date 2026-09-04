package com.codinglair.taf.runtime.secret;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.definition.SecretKind;
import com.codinglair.taf.runtime.definition.SecretProvisioningContext;
import com.codinglair.taf.runtime.definition.TransientSecretValue;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Local Jasypt secret provisioner")
class JasyptSecretProvisionerTest {
  @Test
  @DisplayName("protects distinct values into nondeterministic references resolved by PWD-001")
  void protectsAndResolvesWithoutExposingValues() {
    String key = "key-" + UUID.randomUUID();
    String plaintext = "value-" + UUID.randomUUID();
    JasyptSecretProvider provider = new JasyptSecretProvider(name -> key, "MASTER_KEY");
    JasyptSecretProvisioner provisioner = new JasyptSecretProvisioner(provider);
    SecretProvisioningContext context =
        new SecretProvisioningContext(
            "demo", "local", "inputs.csv", "TC1", "credential", "test", true);
    String first;
    String second;
    try (TransientSecretValue value = TransientSecretValue.of(plaintext.toCharArray())) {
      first = provisioner.protect(value, SecretKind.PASSWORD, context);
      second = provisioner.protect(value, SecretKind.PASSWORD, context);
    }

    assertThat(first).startsWith("secret://jasypt/").isNotEqualTo(second);
    try (ResolvedSecret resolved = provider.resolve(SecretReference.parse(first))) {
      assertThat(resolved.useAsString()).isEqualTo(plaintext);
    }
  }
}
