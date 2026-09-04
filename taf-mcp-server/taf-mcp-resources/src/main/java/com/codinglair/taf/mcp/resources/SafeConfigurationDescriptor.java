package com.codinglair.taf.mcp.resources;

/** Value-free configuration metadata safe for an MCP discovery response. */
public record SafeConfigurationDescriptor(String key, boolean configured, String source) {
  public SafeConfigurationDescriptor {
    key = requireText(key, "key");
    source = requireText(source, "source");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}
