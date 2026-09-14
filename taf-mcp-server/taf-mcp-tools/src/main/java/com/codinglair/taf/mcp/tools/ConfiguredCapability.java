package com.codinglair.taf.mcp.tools;

import java.util.Set;

/** Administrator-supplied safe policy state for one configured capability instance. */
public record ConfiguredCapability(
    String capabilityId,
    String instance,
    String environment,
    boolean installed,
    boolean ready,
    String ownershipMode,
    String isolationMode,
    Set<String> permittedOperations) {
  public ConfiguredCapability {
    permittedOperations = Set.copyOf(permittedOperations);
  }
}
