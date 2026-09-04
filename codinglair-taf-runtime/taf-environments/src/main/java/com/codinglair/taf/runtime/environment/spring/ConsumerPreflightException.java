package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.PreflightResult;

/**
 * Aggregated failure raised by the legacy facade.
 *
 * @deprecated inject and use the Runtime Core preflight service and exception at new boundaries.
 */
@Deprecated(forRemoval = false, since = "1.0")
public final class ConsumerPreflightException extends IllegalStateException {
  private final PreflightResult result;

  ConsumerPreflightException(PreflightResult result) {
    super(message(result));
    this.result = result;
  }

  public PreflightResult result() {
    return result;
  }

  private static String message(PreflightResult result) {
    StringBuilder message =
        new StringBuilder("Consumer preflight failed with ")
            .append(result.checks().size())
            .append(" diagnostic(s):");
    result
        .checks()
        .forEach(
            check ->
                message
                    .append(System.lineSeparator())
                    .append("- ")
                    .append(check.checkId())
                    .append(": ")
                    .append(check.message())
                    .append("; ")
                    .append(check.correctiveAction()));
    return message.toString();
  }
}
