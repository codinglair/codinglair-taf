package com.codinglair.taf.runtime.secret;

import java.util.Objects;

public final class EnvironmentSecretProvider implements SecretProvider {
  private final EnvironmentValueSource environment;

  public EnvironmentSecretProvider(EnvironmentValueSource environment) {
    this.environment = Objects.requireNonNull(environment);
  }

  @Override
  public String id() {
    return "env";
  }

  @Override
  public void verifyReady() {}

  @Override
  public ResolvedSecret resolve(SecretReference reference) {
    if (!id().equals(reference.provider()))
      throw new SecretResolutionException(
          SecretResolutionException.Category.MALFORMED_REFERENCE,
          "Reference does not select the environment provider");
    String value = environment.get(reference.providerPayload());
    if (value == null || value.isBlank())
      throw new SecretResolutionException(
          SecretResolutionException.Category.PROVIDER_UNAVAILABLE,
          "Referenced environment credential is unavailable");
    return ResolvedSecret.of(value.toCharArray());
  }
}
