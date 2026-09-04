package com.codinglair.taf.runtime.environment;

import java.util.Locale;

/** Selects whether a container belongs to one request or is leased across requests. */
public enum ContainerLifecycle {
  ISOLATED,
  SHARED;

  public static final String PROPERTY = "taf.container.lifecycle";
  public static final String SHARED_KEY_PROPERTY = "taf.container.shared-key";

  static ContainerLifecycle from(EnvironmentRequest request) {
    String configured = request.properties().getOrDefault(PROPERTY, ISOLATED.name());
    try {
      return valueOf(configured.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException(
          "Property " + PROPERTY + " must be 'isolated' or 'shared'", failure);
    }
  }
}
