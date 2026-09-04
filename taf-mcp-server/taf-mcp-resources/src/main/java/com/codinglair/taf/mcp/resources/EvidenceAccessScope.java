package com.codinglair.taf.mcp.resources;

/** Internal access attributes stored alongside evidence but never returned as resource metadata. */
public record EvidenceAccessScope(String ownerUserId, String project, String environment) {
  public EvidenceAccessScope {
    ownerUserId = requireText(ownerUserId, "ownerUserId");
    project = requireText(project, "project");
    environment = requireText(environment, "environment");
  }

  public boolean permits(ResourceRequestContext caller) {
    return ownerUserId.equals(caller.identity().userId())
        && project.equals(caller.project())
        && environment.equals(caller.environment());
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}
