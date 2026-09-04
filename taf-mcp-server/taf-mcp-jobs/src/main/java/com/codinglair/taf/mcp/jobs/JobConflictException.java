package com.codinglair.taf.mcp.jobs;

/** Signals an optimistic-concurrency conflict. */
public final class JobConflictException extends RuntimeException {
  public JobConflictException(JobId id, long expected, long actual) {
    super("Job " + id.value() + " version conflict: expected " + expected + " but was " + actual);
  }
}
