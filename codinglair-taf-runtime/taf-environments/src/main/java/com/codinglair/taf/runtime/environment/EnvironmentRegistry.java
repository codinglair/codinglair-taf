package com.codinglair.taf.runtime.environment;

import java.util.Map;

/** Registry selecting providers by environment type and execution mode. */
public interface EnvironmentRegistry {
  void register(EnvironmentType type, EnvironmentMode mode, EnvironmentProvider provider);

  EnvironmentProvider providerFor(EnvironmentType type, EnvironmentMode mode);

  Map<ProviderKey, EnvironmentProvider> providers();

  record ProviderKey(EnvironmentType type, EnvironmentMode mode) {}
}
