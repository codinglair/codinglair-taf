package com.codinglair.taf.mcp.resources;

import com.codinglair.taf.core.Capability;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-owned capability descriptor enriched only with installation and artifact version state.
 */
public record RuntimeCapabilityDescriptor(
    Capability capability,
    String runtimeVersion,
    InstallationStatus status,
    List<String> operations,
    List<String> limitations,
    List<String> requiredConfiguration) {
  public enum InstallationStatus {
    INSTALLED,
    ABSENT
  }

  public RuntimeCapabilityDescriptor {
    Objects.requireNonNull(capability, "capability");
    runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
    Objects.requireNonNull(status, "status");
    operations = List.copyOf(operations);
    limitations = List.copyOf(limitations);
    requiredConfiguration = List.copyOf(requiredConfiguration);
  }

  public RuntimeCapabilityDescriptor(
      Capability capability, String runtimeVersion, InstallationStatus status) {
    this(capability, runtimeVersion, status, List.of(), List.of(), List.of());
  }

  public String id() {
    return capability.name();
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}
