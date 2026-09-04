package com.codinglair.taf.mcp.resources;

import com.codinglair.taf.core.Capability;
import java.util.Objects;

/**
 * Runtime-owned capability descriptor enriched only with installation and artifact version state.
 */
public record RuntimeCapabilityDescriptor(
    Capability capability, String runtimeVersion, InstallationStatus status) {
  public enum InstallationStatus {
    INSTALLED,
    ABSENT
  }

  public RuntimeCapabilityDescriptor {
    Objects.requireNonNull(capability, "capability");
    runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
    Objects.requireNonNull(status, "status");
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
