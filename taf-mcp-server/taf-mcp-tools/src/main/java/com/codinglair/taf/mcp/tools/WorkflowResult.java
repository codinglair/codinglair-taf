package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.jobs.JobReference;
import java.util.List;

public record WorkflowResult(
    WorkflowOutcome outcome, String summary, List<JobReference> references) {
  public WorkflowResult {
    if (outcome == null || summary == null || summary.isBlank() || summary.length() > 4096)
      throw new IllegalArgumentException("bounded outcome and summary are required");
    references = List.copyOf(references);
  }
}
