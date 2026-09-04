package com.codinglair.taf.mcp.tools;

import java.util.List;

public record ScaffoldResult(
    Status status, String summary, List<String> proposedDiff, ScaffoldProvenance provenance) {
  public enum Status {
    APPLIED,
    REVERTED,
    APPROVAL_REQUIRED,
    DENIED,
    CONFLICT,
    COMPILATION_FAILED,
    FAILED
  }

  public ScaffoldResult {
    proposedDiff = List.copyOf(proposedDiff);
  }
}
