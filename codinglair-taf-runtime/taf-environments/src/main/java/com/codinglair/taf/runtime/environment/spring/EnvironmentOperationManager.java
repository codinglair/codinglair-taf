package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.*;
import java.util.Objects;
import java.util.function.Function;

/** Provides failure- and cancellation-safe operation-scoped resource ownership. */
public final class EnvironmentOperationManager {
  private final EnvironmentRegistry registry;
  private final TafEnvironmentProperties configuration;

  public EnvironmentOperationManager(EnvironmentRegistry registry) {
    this(registry, null);
  }

  public EnvironmentOperationManager(
      EnvironmentRegistry registry, TafEnvironmentProperties configuration) {
    this.registry = Objects.requireNonNull(registry);
    this.configuration = configuration;
  }

  public <T> T withResource(
      EnvironmentRequest request, Function<EnvironmentResource, T> operation) {
    EnvironmentRequest configured =
        configuration == null
            ? request
            : new EnvironmentRequest(
                request.resourceName(),
                request.type(),
                configuration.getMode(),
                request.capabilities(),
                request.properties(),
                configuration.getReadinessTimeout());
    EnvironmentProvider provider = registry.providerFor(configured.type(), configured.mode());
    EnvironmentResource resource = provider.provision(configured);
    try {
      return operation.apply(resource);
    } finally {
      provider.release(resource.id());
    }
  }
}
