package com.codinglair.taf.runtime.core.preflight;

import java.util.List;

/** Aggregated failure raised before consumer test activity begins. */
public final class ConsumerPreflightException extends IllegalStateException {
  private final List<PreflightDiagnostic> diagnostics;

  ConsumerPreflightException(List<PreflightDiagnostic> diagnostics) {
    super(message(diagnostics));
    this.diagnostics = List.copyOf(diagnostics);
  }

  public List<PreflightDiagnostic> diagnostics() {
    return diagnostics;
  }

  private static String message(List<PreflightDiagnostic> diagnostics) {
    StringBuilder message =
        new StringBuilder("Consumer preflight failed with ")
            .append(diagnostics.size())
            .append(" diagnostic(s):");
    diagnostics.forEach(
        diagnostic ->
            message
                .append(System.lineSeparator())
                .append("- ")
                .append(diagnostic.checkId())
                .append(": ")
                .append(diagnostic.message())
                .append("; ")
                .append(diagnostic.correctiveAction()));
    return message.toString();
  }
}
