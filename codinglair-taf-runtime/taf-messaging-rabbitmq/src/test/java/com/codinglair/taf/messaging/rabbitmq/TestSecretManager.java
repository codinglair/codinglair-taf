package com.codinglair.taf.messaging.rabbitmq;

import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretRequestContext;

final class TestSecretManager implements SecretManager {
  static final TestSecretManager GUEST = new TestSecretManager("guest");
  private final char[] value;

  TestSecretManager(String value) {
    this.value = value.toCharArray();
  }

  @Override
  public ResolvedSecret resolve(String reference, SecretRequestContext context) {
    return ResolvedSecret.of(value);
  }

  @Override
  public void verifyReady(String reference) {}
}
