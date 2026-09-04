package com.codinglair.taf.runtime.environment;

import java.util.Map;

/** A provisioned resource consumed by controllers but owned by its provider. */
public interface EnvironmentResource extends AutoCloseable {
  String id();

  EnvironmentType type();

  EnvironmentMode mode();

  Map<String, String> properties();

  EnvironmentDiagnostic diagnose();

  void cleanup();

  @Override
  default void close() {
    cleanup();
  }
}
