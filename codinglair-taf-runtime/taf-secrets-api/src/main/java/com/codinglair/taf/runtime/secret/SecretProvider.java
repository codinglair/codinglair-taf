package com.codinglair.taf.runtime.secret;

/** Backend-specific resolution SPI. Implementations must not log or cache resolved values. */
public interface SecretProvider {
  String id();

  void verifyReady();

  ResolvedSecret resolve(SecretReference reference);
}
