package com.codinglair.taf.messaging.aws.common;

/**
 * Stable, sanitized AWS operation failure. The SDK cause is retained for local diagnostics only.
 */
public final class AwsControllerException extends RuntimeException {
  private final String service;
  private final String operation;
  private final AwsFailureCategory category;
  private final boolean retryable;

  public AwsControllerException(String service, String operation, Throwable cause) {
    this(service, operation, AwsFailureClassifier.classify(cause), cause);
  }

  AwsControllerException(
      String service,
      String operation,
      AwsFailureClassifier.Classification classification,
      Throwable cause) {
    super(
        service
            + " operation '"
            + operation
            + "' failed; verify configuration, authorization, resource identity, and service availability",
        cause);
    this.service = service;
    this.operation = operation;
    category = classification.category();
    retryable = classification.retryable();
  }

  public String service() {
    return service;
  }

  public String operation() {
    return operation;
  }

  public AwsFailureCategory category() {
    return category;
  }

  public boolean retryable() {
    return retryable;
  }
}
