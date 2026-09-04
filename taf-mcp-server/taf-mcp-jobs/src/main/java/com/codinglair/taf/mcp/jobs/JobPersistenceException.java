package com.codinglair.taf.mcp.jobs;

/** Structured persistence-boundary failure that never includes job payload data. */
public final class JobPersistenceException extends RuntimeException {
  public JobPersistenceException(String operation, JobId id, Throwable cause) {
    super(
        "Unable to "
            + operation
            + " durable job "
            + id.value()
            + "; inspect repository availability and integrity",
        cause);
  }
}
