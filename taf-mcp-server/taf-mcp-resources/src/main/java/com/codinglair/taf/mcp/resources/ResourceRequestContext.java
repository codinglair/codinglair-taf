package com.codinglair.taf.mcp.resources;

import com.codinglair.taf.mcp.security.CallerIdentity;

/** Transport-neutral caller scope used to derive server-owned authorization attributes. */
public record ResourceRequestContext(CallerIdentity identity, String project, String environment) {
  public ResourceRequestContext {
    if (identity == null || project == null || project.isBlank()) {
      throw new IllegalArgumentException("identity and project are required");
    }
    environment = environment == null || environment.isBlank() ? "default" : environment;
  }
}
