package com.codinglair.taf.api.rest;

/** Structured REST capability failure. */
public final class RestControllerException extends RuntimeException {
  private final String operation;
  private final String correctiveAction;

  RestControllerException(String operation, String correctiveAction, Throwable cause) {
    super("REST operation '" + operation + "' failed; " + correctiveAction, cause);
    this.operation = operation;
    this.correctiveAction = correctiveAction;
  }

  public String operation() {
    return operation;
  }

  public String correctiveAction() {
    return correctiveAction;
  }
}
