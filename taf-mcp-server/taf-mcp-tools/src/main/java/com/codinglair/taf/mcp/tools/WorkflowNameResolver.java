package com.codinglair.taf.mcp.tools;

/** Server-owned mapping from validated structured selection to an allowlisted worker workflow. */
@FunctionalInterface
public interface WorkflowNameResolver {
  String resolve(ToolOperation operation, String environment, java.util.Optional<String> selector);
}
