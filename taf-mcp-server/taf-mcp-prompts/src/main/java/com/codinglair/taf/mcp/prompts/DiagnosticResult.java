package com.codinglair.taf.mcp.prompts;

import java.util.List;
import java.util.Map;

/** Sanitized, bounded result of one allowlisted diagnostic. */
public record DiagnosticResult(
    DiagnosticKind kind,
    String jobId,
    Map<String, String> summary,
    List<String> resourceReferences) {
  public DiagnosticResult {
    summary = Map.copyOf(summary);
    resourceReferences = List.copyOf(resourceReferences);
    if (summary.size() > 20 || resourceReferences.size() > 100) {
      throw new IllegalArgumentException("diagnostic result exceeds bounds");
    }
  }
}
