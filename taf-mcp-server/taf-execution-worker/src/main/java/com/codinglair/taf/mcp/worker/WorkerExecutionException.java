package com.codinglair.taf.mcp.worker;

/** Structured, payload-free worker boundary failure. */
public final class WorkerExecutionException extends RuntimeException {
  private final String code;

  public WorkerExecutionException(String code, String message) {
    super(message);
    this.code = code;
  }

  public WorkerExecutionException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
