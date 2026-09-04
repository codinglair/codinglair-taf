package com.codinglair.taf.runtime.definition;

import java.util.Arrays;

/** Short-lived authoring value that never renders or compares its payload. */
public final class TransientSecretValue implements AutoCloseable {
  private final char[] value;

  private TransientSecretValue(char[] value) {
    this.value = value;
  }

  public static TransientSecretValue of(char[] value) {
    if (value == null || value.length == 0)
      throw new IllegalArgumentException("Secret value is blank");
    return new TransientSecretValue(value.clone());
  }

  public <T> T use(SecretValueFunction<T> function) {
    return function.apply(value);
  }

  @Override
  public void close() {
    Arrays.fill(value, '\0');
  }

  @Override
  public String toString() {
    return "TransientSecretValue[REDACTED]";
  }

  @FunctionalInterface
  public interface SecretValueFunction<T> {
    T apply(char[] value);
  }
}
