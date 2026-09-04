package com.codinglair.taf.mcp.jobs;

/** Signals a state transition rejected by the durable job state machine. */
public final class InvalidJobTransitionException extends IllegalStateException {
  public InvalidJobTransitionException(JobState source, JobState target) {
    super("Invalid job transition from " + source + " to " + target);
  }
}
