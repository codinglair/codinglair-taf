package com.codinglair.taf.mcp.stdio;

import com.codinglair.taf.mcp.resources.McpResourceService;
import com.codinglair.taf.mcp.resources.ResourceQuery;
import com.codinglair.taf.mcp.resources.ResourceRequestContext;
import org.springframework.ai.mcp.annotation.McpResource;

/** Bounded Spring AI resource facade over the shared MCP-005 authorization boundary. */
public final class StdioResources {
  private static final int DEFAULT_PAGE_SIZE = 50;
  private final McpResourceService resources;
  private final TafMcpStdioProperties properties;

  public StdioResources(McpResourceService resources, TafMcpStdioProperties properties) {
    this.resources = resources;
    this.properties = properties;
  }

  @McpResource(
      name = "taf-capabilities",
      uri = "taf://capabilities",
      description = "Installed and absent TAF Runtime capabilities",
      mimeType = "text/plain")
  public String capabilities() {
    return resources
        .discoverCapabilities(context(), new ResourceQuery("", DEFAULT_PAGE_SIZE, null))
        .toString();
  }

  @McpResource(
      name = "taf-documentation",
      uri = "taf://documentation",
      description = "Versioned bounded TAF documentation catalog",
      mimeType = "text/plain")
  public String documentation() {
    return resources
        .discoverDocumentation(context(), new ResourceQuery("", DEFAULT_PAGE_SIZE, null))
        .toString();
  }

  @McpResource(
      name = "taf-environments",
      uri = "taf://environments",
      description = "Sanitized TAF environment readiness",
      mimeType = "text/plain")
  public String environments() {
    return resources
        .discoverEnvironments(context(), new ResourceQuery("", DEFAULT_PAGE_SIZE, null))
        .toString();
  }

  private ResourceRequestContext context() {
    return new ResourceRequestContext(
        properties.identity(), properties.getProject(), properties.getEnvironment());
  }
}
