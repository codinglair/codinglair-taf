package com.codinglair.taf.runtime.environment;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent registry with deterministic duplicate rejection. */
public final class DefaultEnvironmentRegistry implements EnvironmentRegistry {
  private final ConcurrentHashMap<ProviderKey, EnvironmentProvider> providers =
      new ConcurrentHashMap<>();

  @Override
  public void register(EnvironmentType type, EnvironmentMode mode, EnvironmentProvider provider) {
    ProviderKey key =
        new ProviderKey(Objects.requireNonNull(type, "type"), Objects.requireNonNull(mode, "mode"));
    Objects.requireNonNull(provider, "provider");
    if (!provider.supportedModes().contains(mode)) {
      throw new IllegalArgumentException(
          "Provider " + provider.id() + " does not support mode " + mode);
    }
    if (providers.putIfAbsent(key, provider) != null) {
      throw new IllegalStateException(
          "A provider is already registered for " + type.name() + "/" + mode);
    }
  }

  @Override
  public EnvironmentProvider providerFor(EnvironmentType type, EnvironmentMode mode) {
    EnvironmentProvider provider = providers.get(new ProviderKey(type, mode));
    if (provider == null) {
      throw new IllegalArgumentException(
          "No environment provider registered for " + type.name() + "/" + mode);
    }
    return provider;
  }

  @Override
  public Map<ProviderKey, EnvironmentProvider> providers() {
    return Map.copyOf(providers);
  }
}
