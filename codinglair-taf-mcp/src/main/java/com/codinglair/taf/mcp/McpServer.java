package com.codinglair.taf.mcp;

/**
 * MCP Server provides governed standards-based access to Runtime.
 *
 * <p>This server implements the Model Context Protocol (MCP) and exposes coarse-grained workflows:
 * discover, validate, scaffold, build, execute, inspect, retrieve, cancel, and report.
 *
 * @author Codinglair TAF Team
 */
public class McpServer {

  /**
   * Gets the MCP server version.
   *
   * @return version string
   */
  public String getVersion() {
    return "1.0.0-SNAPSHOT";
  }
}
