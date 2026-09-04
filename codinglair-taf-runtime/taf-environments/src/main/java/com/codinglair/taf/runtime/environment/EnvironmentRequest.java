package com.codinglair.taf.runtime.environment;

import com.codinglair.taf.core.Capability;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable input to provider selection, preflight, and provisioning. */
public record EnvironmentRequest(
    String resourceName,
    EnvironmentType type,
    EnvironmentMode mode,
    Set<Capability> capabilities,
    Map<String, String> properties,
    Duration timeout) {

  public EnvironmentRequest {
    if (resourceName == null || resourceName.isBlank()) {
      throw new IllegalArgumentException("Resource name must not be blank");
    }
    resourceName = resourceName.trim();
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(mode, "mode");
    capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Timeout must be positive");
    }
  }
}
