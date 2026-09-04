package com.codinglair.taf.mcp.http;

import com.codinglair.taf.mcp.resources.McpResourceService;
import com.codinglair.taf.mcp.resources.ResourceQuery;
import com.codinglair.taf.mcp.resources.ResourceRequestContext;
import org.springframework.ai.mcp.annotation.McpResource;

/** Bounded resource facade using authenticated request context. */
public final class HttpResources {
  private static final int PAGE_SIZE = 50;
  private final McpResourceService resources;
  private final HttpCallerContext caller;

  HttpResources(McpResourceService resources, HttpCallerContext caller) {
    this.resources = resources;
    this.caller = caller;
  }

  @McpResource(
      name = "taf-capabilities",
      uri = "taf://capabilities",
      description = "Installed and absent TAF Runtime capabilities",
      mimeType = "text/plain")
  public String capabilities() {
    caller.requireScope("taf.resources.read");
    return resources.discoverCapabilities(context(), query()).toString();
  }

  @McpResource(
      name = "taf-documentation",
      uri = "taf://documentation",
      description = "Versioned bounded TAF documentation catalog",
      mimeType = "text/plain")
  public String documentation() {
    caller.requireScope("taf.resources.read");
    return resources.discoverDocumentation(context(), query()).toString();
  }

  @McpResource(
      name = "taf-environments",
      uri = "taf://environments",
      description = "Sanitized TAF environment readiness",
      mimeType = "text/plain")
  public String environments() {
    caller.requireScope("taf.resources.read");
    return resources.discoverEnvironments(context(), query()).toString();
  }

  private ResourceRequestContext context() {
    return new ResourceRequestContext(caller.identity(), caller.project(), caller.environment());
  }

  private static ResourceQuery query() {
    return new ResourceQuery("", PAGE_SIZE, null);
  }
}
