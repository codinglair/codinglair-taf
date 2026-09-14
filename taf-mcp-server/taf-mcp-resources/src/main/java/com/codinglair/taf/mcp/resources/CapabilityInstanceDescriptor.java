package com.codinglair.taf.mcp.resources;

import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import java.util.List;

/** Safe, configured logical capability instance exposed to MCP clients. */
public record CapabilityInstanceDescriptor(
    String capabilityId,
    String instance,
    String resourceAlias,
    String ownershipMode,
    String isolationMode,
    EnvironmentStatus readiness,
    List<String> diagnostics) {
  public CapabilityInstanceDescriptor {
    capabilityId = text(capabilityId, "capabilityId");
    instance = text(instance, "instance");
    resourceAlias = text(resourceAlias, "resourceAlias");
    ownershipMode = text(ownershipMode, "ownershipMode");
    isolationMode = text(isolationMode, "isolationMode");
    if (readiness == null) throw new IllegalArgumentException("readiness is required");
    diagnostics = List.copyOf(diagnostics);
    if (!capabilityId.matches("[A-Za-z0-9._-]+")
        || !instance.matches("[A-Za-z0-9._-]+")
        || !resourceAlias.matches("[A-Za-z0-9._/-]+")
        || !ownershipMode.matches("[A-Z_]+")
        || !isolationMode.matches("[A-Z_]+")
        || diagnostics.stream().anyMatch(value -> !value.matches("[A-Za-z0-9._-]{1,128}"))) {
      throw new IllegalArgumentException(
          "capability instance metadata must contain safe identifiers");
    }
  }

  private static String text(String value, String field) {
    if (value == null
        || value.isBlank()
        || value.length() > 128
        || value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(field + " must be safe bounded text");
    }
    return value;
  }
}
