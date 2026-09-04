package com.codinglair.taf.web.playwright;

/** Actionable capability failure without endpoint or credential disclosure. */
public final class PlaywrightControllerException extends RuntimeException {
  private final String operation;

  public PlaywrightControllerException(String operation, String message, Throwable cause) {
    super("Playwright operation '" + operation + "' failed: " + message, cause);
    this.operation = operation;
  }

  public String operation() {
    return operation;
  }
}
