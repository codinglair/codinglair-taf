package com.codinglair.taf.observability;

/** Preserves cancellation when an eventual telemetry assertion is interrupted. */
public final class ObservabilityAssertionException extends RuntimeException {
  public ObservabilityAssertionException(String message, InterruptedException cause) {
    super(message, cause);
  }
}
