package com.codinglair.taf.database.lifecycle;

public final class DatabaseLifecycleException extends RuntimeException {
  private final String executionId;
  private final String database;
  private final String correctiveAction;

  public DatabaseLifecycleException(
      String message,
      String executionId,
      String database,
      String correctiveAction,
      Throwable cause) {
    super(message, cause);
    this.executionId = executionId;
    this.database = database;
    this.correctiveAction = correctiveAction;
  }

  public String executionId() {
    return executionId;
  }

  public String database() {
    return database;
  }

  public String correctiveAction() {
    return correctiveAction;
  }
}
