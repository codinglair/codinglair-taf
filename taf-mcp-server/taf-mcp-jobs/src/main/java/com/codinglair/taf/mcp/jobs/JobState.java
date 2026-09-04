package com.codinglair.taf.mcp.jobs;

/** Durable lifecycle states for asynchronous MCP work. */
public enum JobState {
  QUEUED,
  RUNNING,
  CANCEL_REQUESTED,
  RECOVERY_PENDING,
  SUCCEEDED,
  FAILED,
  CANCELLED;

  public boolean terminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED;
  }
}
