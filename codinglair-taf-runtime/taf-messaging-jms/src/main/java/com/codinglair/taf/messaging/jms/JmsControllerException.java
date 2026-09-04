package com.codinglair.taf.messaging.jms;

/** Sanitized actionable JMS failure without provider endpoint or credential values. */
public final class JmsControllerException extends RuntimeException {
  private final String operation;
  private final String correctiveAction;

  JmsControllerException(String operation, String correctiveAction, Throwable cause) {
    super(
        "JMS " + operation + " failed; " + correctiveAction,
        cause == null ? null : new SanitizedCause(cause.getClass().getSimpleName()));
    this.operation = operation;
    this.correctiveAction = correctiveAction;
  }

  public String operation() {
    return operation;
  }

  public String correctiveAction() {
    return correctiveAction;
  }

  private static final class SanitizedCause extends RuntimeException {
    private SanitizedCause(String message) {
      super(message, null, false, false);
    }
  }
}
