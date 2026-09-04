package com.codinglair.taf.runtime.environment;

import java.util.Collection;
import java.util.Set;

/** Provider SPI for external or container-backed resources. */
public interface EnvironmentProvider extends AutoCloseable {
  String id();

  Set<EnvironmentMode> supportedModes();

  PreflightResult preflight(EnvironmentRequest request);

  EnvironmentResource provision(EnvironmentRequest request);

  Collection<EnvironmentResource> activeResources();

  void release(String resourceId);

  void cleanup();

  @Override
  default void close() {
    cleanup();
  }
}
