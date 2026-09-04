package com.codinglair.taf.database;

public final class DatabaseException extends RuntimeException {
  public DatabaseException(
      String connection, String operation, String correctiveAction, Throwable cause) {
    super(
        "Database operation '"
            + operation
            + "' failed for SUT connection '"
            + connection
            + "'; "
            + correctiveAction,
        cause);
  }
}
