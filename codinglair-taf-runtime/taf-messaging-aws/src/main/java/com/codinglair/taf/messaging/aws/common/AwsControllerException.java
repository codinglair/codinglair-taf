package com.codinglair.taf.messaging.aws.common;

/**
 * Stable, sanitized AWS operation failure. The SDK cause is retained for local diagnostics only.
 */
public final class AwsControllerException extends RuntimeException {
  private final String service;
  private final String operation;

  public AwsControllerException(String service, String operation, Throwable cause) {
    super(
        service
            + " operation '"
            + operation
            + "' failed; verify configuration, authorization, resource identity, and service availability",
        cause);
    this.service = service;
    this.operation = operation;
  }

  public String service() {
    return service;
  }

  public String operation() {
    return operation;
  }
}
