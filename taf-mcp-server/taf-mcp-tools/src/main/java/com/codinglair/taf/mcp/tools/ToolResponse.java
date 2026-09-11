package com.codinglair.taf.mcp.tools;

import java.util.List;
import java.util.Objects;

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
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    Objects.requireNonNull(status, "status");
    if (summary == null || summary.isBlank()) {
      throw new IllegalArgumentException("summary is required");
    }
    resultReferences = List.copyOf(Objects.requireNonNull(resultReferences, "resultReferences"));
  }
}
