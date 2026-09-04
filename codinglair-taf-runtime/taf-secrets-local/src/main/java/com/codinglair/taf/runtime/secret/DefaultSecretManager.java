package com.codinglair.taf.runtime.secret;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DefaultSecretManager implements SecretManager {
  private final Map<String, SecretProvider> providers;
  private final SecretAuditSink audit;

  public DefaultSecretManager(List<SecretProvider> providers, SecretAuditSink audit) {
    this.audit = Objects.requireNonNull(audit);
    Map<String, SecretProvider> indexed = new HashMap<>();
    for (SecretProvider provider : List.copyOf(providers)) {
      if (indexed.putIfAbsent(provider.id(), provider) != null)
        throw new SecretResolutionException(
            SecretResolutionException.Category.DUPLICATE_PROVIDER,
            "Duplicate secret provider registration: " + provider.id());
    }
    this.providers = Map.copyOf(indexed);
  }

  @Override
  public ResolvedSecret resolve(String rawReference, SecretRequestContext context) {
    Objects.requireNonNull(context, "context");
    SecretReference reference = parse(rawReference);
    long started = System.nanoTime();
    String outcome = "DENIED";
    try {
      if (!context.authorized())
        throw new SecretResolutionException(
            SecretResolutionException.Category.ACCESS_DENIED, "Secret access is not authorized");
      outcome = "FAILED";
      ResolvedSecret resolved = provider(reference).resolve(reference);
      outcome = "RESOLVED";
      return resolved;
    } catch (RuntimeException failure) {
      throw failure;
    } finally {
      audit.record(
          new SecretAuditEvent(
              reference.provider(),
              reference.correlationToken(),
              context.requestingComponent(),
              context.sessionId(),
              context.environment(),
              outcome,
              context.authorized(),
              Instant.now(),
              System.nanoTime() - started));
    }
  }

  @Override
  public void verifyReady(String rawReference) {
    provider(parse(rawReference)).verifyReady();
  }

  private SecretProvider provider(SecretReference reference) {
    SecretProvider provider = providers.get(reference.provider());
    if (provider == null)
      throw new SecretResolutionException(
          SecretResolutionException.Category.PROVIDER_UNAVAILABLE,
          "Selected secret provider is unavailable");
    return provider;
  }

  private static SecretReference parse(String raw) {
    try {
      return SecretReference.parse(raw);
    } catch (IllegalArgumentException invalid) {
      throw new SecretResolutionException(
          SecretResolutionException.Category.MALFORMED_REFERENCE, invalid.getMessage());
    }
  }
}
