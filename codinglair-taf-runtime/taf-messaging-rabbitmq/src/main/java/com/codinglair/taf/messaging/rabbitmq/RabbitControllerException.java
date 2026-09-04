package com.codinglair.taf.messaging.rabbitmq;

import com.codinglair.taf.runtime.environment.DiagnosticSanitizer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/** Sanitized, actionable RabbitMQ adapter failure. */
public final class RabbitControllerException extends RuntimeException {
  private static final Pattern ENDPOINT =
      Pattern.compile("(?i)(?:amqps?://)?[^\\s/:]+(?::\\d{1,5})?");
  private final String operation;
  private final String correctiveAction;

  RabbitControllerException(String operation, String correctiveAction, Throwable cause) {
    super("RabbitMQ " + operation + " failed; " + correctiveAction, safeCause(cause));
    this.operation = operation;
    this.correctiveAction = correctiveAction;
  }

  public String operation() {
    return operation;
  }

  public String correctiveAction() {
    return correctiveAction;
  }

  private static Throwable safeCause(Throwable cause) {
    return sanitize(cause, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static Throwable sanitize(Throwable cause, Set<Throwable> visited) {
    if (cause == null) return null;
    if (!visited.add(cause)) return new SanitizedCause("cause cycle", null);
    String message = DiagnosticSanitizer.sanitize(cause.getMessage());
    return new SanitizedCause(
        ENDPOINT.matcher(message).replaceAll("[DETAIL]"), sanitize(cause.getCause(), visited));
  }

  private static final class SanitizedCause extends RuntimeException {
    private SanitizedCause(String message, Throwable cause) {
      super(message, cause, false, false);
    }
  }
}
