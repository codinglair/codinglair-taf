package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.jobs.JobId;

@FunctionalInterface
public interface WorkflowRunner {
  WorkflowResult run(JobId jobId, ToolRequest request);
}
