package com.codinglair.taf.virtualization.wiremock;

/** Structured capability failure with an actionable, secret-safe message. */
public final class WireMockVirtualizationException extends RuntimeException {
  private final String operation;
  private final String correctiveAction;

  public WireMockVirtualizationException(
      String operation, String message, String correctiveAction, Throwable cause) {
    super(
        "wiremock operation="
            + operation
            + ": "
            + message
            + "; correctiveAction="
            + correctiveAction,
        cause);
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
