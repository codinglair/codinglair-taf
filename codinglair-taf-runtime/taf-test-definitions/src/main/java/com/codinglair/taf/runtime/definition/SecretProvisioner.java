package com.codinglair.taf.runtime.definition;

/** Provider-neutral authoring contract; deliberately separate from execution-time resolution. */
public interface SecretProvisioner {
  String id();

  void verifyReady();

  String protect(
      TransientSecretValue plaintext, SecretKind kind, SecretProvisioningContext context);
}
