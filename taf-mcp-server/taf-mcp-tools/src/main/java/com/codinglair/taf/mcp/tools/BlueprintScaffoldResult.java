package com.codinglair.taf.mcp.tools;

import java.util.List;

/** Bounded, sanitized result returned by capability-driven scaffolding. */
public record BlueprintScaffoldResult(
    Status status,
    String blueprintVersion,
    List<String> contributions,
    List<String> files,
    List<BlueprintCompositionEngine.Diagnostic> diagnostics,
    String summary) {
  public BlueprintScaffoldResult {
    contributions = List.copyOf(contributions);
    files = List.copyOf(files);
    diagnostics = List.copyOf(diagnostics);
  }

  public enum Status {
    VALIDATED,
    APPLIED,
    VALIDATION_FAILED,
    APPROVAL_REQUIRED,
    DENIED,
    CONFLICT,
    PREFLIGHT_FAILED,
    FAILED
  }
}
