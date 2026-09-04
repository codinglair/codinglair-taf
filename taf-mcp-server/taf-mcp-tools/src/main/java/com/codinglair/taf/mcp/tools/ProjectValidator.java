package com.codinglair.taf.mcp.tools;

@FunctionalInterface
public interface ProjectValidator {
  WorkflowResult validate(ToolRequest request);
}
