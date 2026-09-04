package com.codinglair.taf.mcp.prompts;

/** Closed set of read-only diagnostics; caller-provided commands and queries are impossible. */
public enum DiagnosticKind {
  REPORT_METADATA,
  EVIDENCE_INVENTORY,
  FAILURE_SUMMARY
}
