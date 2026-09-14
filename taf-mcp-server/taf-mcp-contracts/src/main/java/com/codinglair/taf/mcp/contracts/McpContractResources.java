package com.codinglair.taf.mcp.contracts;

/** Published locations for the versioned, transport-neutral MCP contract resources. */
public final class McpContractResources {
  /** Classpath root containing the MCP v1 JSON schemas. */
  public static final String V1_SCHEMA_ROOT = "/META-INF/taf/mcp/schema/v1/";

  /** Classpath location of the MCP v1 capability catalog. */
  public static final String V1_CAPABILITY_CATALOG =
      "/META-INF/taf/mcp/catalog/v1/capabilities.json";

  private McpContractResources() {}
}
