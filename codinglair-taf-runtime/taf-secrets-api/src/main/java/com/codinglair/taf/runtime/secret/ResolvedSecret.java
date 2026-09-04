package com.codinglair.taf.runtime.secret;

import java.util.Arrays;
import java.util.Objects;

/** Short-lived secret holder. Callers must close it immediately after deterministic use. */
public final class ResolvedSecret implements AutoCloseable {
  private char[] value;

  private ResolvedSecret(char[] value) {
    this.value = value;
  }

  public static ResolvedSecret of(char[] value) {
    Objects.requireNonNull(value, "value");
    if (value.length == 0) throw new IllegalArgumentException("Resolved secret must not be empty");
    return new ResolvedSecret(value.clone());
  }

  public synchronized String useAsString() {
    if (value == null) throw new IllegalStateException("Resolved secret is closed");
    return new String(value);
  }

  public synchronized boolean isClosed() {
    return value == null;
  }

  @Override
  public synchronized void close() {
    if (value != null) {
      Arrays.fill(value, '\0');
      value = null;
    }
  }

  @Override
  public String toString() {
    return "ResolvedSecret[REDACTED]";
  }
}
