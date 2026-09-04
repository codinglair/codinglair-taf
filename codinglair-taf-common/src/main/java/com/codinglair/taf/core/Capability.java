package com.codinglair.taf.core;

/**
 * Represents a core capability available within the TAF Runtime. This serves as a dependency-light,
 * immutable value object contract.
 *
 * <p>Adheres to RT-001: Core value types, dependency-light contracts, and stable serialization.
 * This class is designed as a Java record to ensure immutability and conciseness.
 */
public record Capability(String name, String descriptor, CapabilityType type) {

  public enum CapabilityType {
    RUNTIME_CORE,
    CONTROLLER,
    ENVIRONMENT,
    REPORTING,
    MESSAGING,
    API,
    TEST_SUPPORT
  }

  /** Constructor validation ensures core constraints are met. */
  public Capability {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Capability name must not be null or empty.");
    }
    if (descriptor == null) {
      throw new IllegalArgumentException("Capability descriptor must not be null.");
    }
  }
}
