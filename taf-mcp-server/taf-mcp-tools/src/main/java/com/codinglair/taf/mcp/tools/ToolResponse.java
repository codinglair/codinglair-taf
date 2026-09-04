package com.codinglair.taf.mcp.tools;

import java.util.List;

public record ToolResponse(
    String requestId,
    Status status,
    WorkflowOutcome outcome,
    String summary,
    String jobReference,
    List<String> resultReferences) {
  public enum Status {
    COMPLETED,
    ACCEPTED,
    FAILED
  }

  public ToolResponse {
    resultReferences = List.copyOf(resultReferences);
  }
}
