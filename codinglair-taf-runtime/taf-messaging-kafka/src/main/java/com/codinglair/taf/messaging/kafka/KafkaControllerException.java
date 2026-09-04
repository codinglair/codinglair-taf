package com.codinglair.taf.messaging.kafka;

import com.codinglair.taf.runtime.environment.DiagnosticSanitizer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/** Sanitized, actionable Kafka adapter failure. */
public final class KafkaControllerException extends RuntimeException {
  private static final Pattern ENDPOINT =
      Pattern.compile(
          "(?i)(?:[a-z][a-z0-9+.-]*://)?(?:localhost|(?:[a-z0-9-]+\\.)*[a-z0-9-]+|(?:\\d{1,3}\\.){3}\\d{1,3}):\\d{1,5}");
  private final String operation;
  private final String correctiveAction;

  KafkaControllerException(String operation, String correctiveAction, Throwable cause) {
    super("Kafka " + operation + " failed; " + correctiveAction, safeCause(cause));
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
    if (!visited.add(cause))
      return new SanitizedCause(cause.getClass().getSimpleName(), "cause cycle", null);
    Throwable sanitizedCause = sanitize(cause.getCause(), visited);
    String message =
        ENDPOINT.matcher(DiagnosticSanitizer.sanitize(cause.getMessage())).replaceAll("[ENDPOINT]");
    return new SanitizedCause(cause.getClass().getSimpleName(), message, sanitizedCause);
  }

  private static final class SanitizedCause extends RuntimeException {
    private SanitizedCause(String type, String message, Throwable cause) {
      super(message.isBlank() ? type : type + ": " + message, cause, false, false);
    }
  }
}
