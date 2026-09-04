package com.codinglair.taf.runtime.definition;

/** Value-free failure from an explicit secret normalization operation. */
public final class SecretNormalizationException extends IllegalStateException {
  SecretNormalizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
