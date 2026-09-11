package com.codinglair.taf.mcp.tools;

/** Validates capability policy before a workflow is dispatched. */
@FunctionalInterface
public interface CapabilityPreflight {
  CapabilityPreflight NONE = request -> {};

  void validate(ToolRequest request);
}
