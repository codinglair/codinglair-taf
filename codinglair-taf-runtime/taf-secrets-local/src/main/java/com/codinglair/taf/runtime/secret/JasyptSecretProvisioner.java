package com.codinglair.taf.runtime.secret;

import com.codinglair.taf.runtime.definition.SecretKind;
import com.codinglair.taf.runtime.definition.SecretProvisioner;
import com.codinglair.taf.runtime.definition.SecretProvisioningContext;
import com.codinglair.taf.runtime.definition.TransientSecretValue;
import java.util.Objects;

/** Local-only Jasypt protection adapter compatible with {@link JasyptSecretProvider}. */
public final class JasyptSecretProvisioner implements SecretProvisioner {
  private final JasyptSecretProvider provider;

  public JasyptSecretProvisioner(JasyptSecretProvider provider) {
    this.provider = Objects.requireNonNull(provider, "provider");
  }

  @Override
  public String id() {
    return "jasypt";
  }

  @Override
  public void verifyReady() {
    provider.verifyReady();
  }

  @Override
  public String protect(
      TransientSecretValue plaintext, SecretKind kind, SecretProvisioningContext context) {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(context, "context");
    if (!context.authorized())
      throw new IllegalStateException("Secret protection is not authorized");
    return plaintext.use(value -> "secret://jasypt/" + provider.encryptForLocalDevelopment(value));
  }
}
