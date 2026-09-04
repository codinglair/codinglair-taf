package com.codinglair.taf.runtime.secret;

/** Authorized neutral orchestrator and the sole operational secret-resolution path. */
public interface SecretManager {
  ResolvedSecret resolve(String reference, SecretRequestContext context);

  void verifyReady(String reference);
}
