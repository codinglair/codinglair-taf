package com.codinglair.taf.runtime.secret;

/** Secret-safe categorized resolution failure. */
public final class SecretResolutionException extends RuntimeException {
  public enum Category {
    ACCESS_DENIED,
    PROVIDER_UNAVAILABLE,
    MALFORMED_REFERENCE,
    DECRYPTION_FAILED,
    DUPLICATE_PROVIDER
  }

  private final Category category;

  public SecretResolutionException(Category category, String message) {
    super(message);
    this.category = category;
  }

  public SecretResolutionException(Category category, String message, Throwable cause) {
    super(message, sanitized(cause));
    this.category = category;
  }

  public Category category() {
    return category;
  }

  private static Throwable sanitized(Throwable cause) {
    return cause == null ? null : new IllegalStateException("Provider operation failed");
  }
}
