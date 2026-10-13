package com.codinglair.taf.mcp;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.ai.mcp.annotation.McpTool;

/** Validates the tool annotations on the production transport adapters before startup completes. */
public final class McpToolCatalogIntegrity {
  public static final int REQUIRED_TOOL_COUNT = 7;

  private McpToolCatalogIntegrity() {}

  public static Set<String> requireComplete(Collection<?> adapters) {
    var names = new HashSet<String>();
    for (var adapter : adapters) {
      for (var method : adapter.getClass().getDeclaredMethods()) {
        var tool = method.getAnnotation(McpTool.class);
        if (tool != null && !names.add(tool.name())) {
          throw incomplete("duplicate tool " + tool.name());
        }
      }
    }
    if (names.size() != REQUIRED_TOOL_COUNT) {
      throw incomplete("expected " + REQUIRED_TOOL_COUNT + " tools but found " + names.size());
    }
    return Set.copyOf(names);
  }

  private static IllegalStateException incomplete(String detail) {
    return new IllegalStateException("Required MCP tool registration is incomplete: " + detail);
  }
}
