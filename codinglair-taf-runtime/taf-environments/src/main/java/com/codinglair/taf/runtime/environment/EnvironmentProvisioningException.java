package com.codinglair.taf.runtime.environment;

import com.codinglair.taf.core.Error;

/** Failure classified explicitly as environment provisioning, never product or automation. */
public final class EnvironmentProvisioningException extends RuntimeException {
  private final String providerId;
  private final String resourceName;
  private final Error error;

  public EnvironmentProvisioningException(
      String providerId, EnvironmentRequest request, String message, String correctiveAction) {
    this(providerId, request, message, correctiveAction, null);
  }

  public EnvironmentProvisioningException(
      String providerId,
      EnvironmentRequest request,
      String message,
      String correctiveAction,
      Throwable cause) {
    super(DiagnosticSanitizer.sanitize(message), sanitizedCause(cause));
    this.providerId = DiagnosticSanitizer.sanitize(providerId);
    this.resourceName = DiagnosticSanitizer.sanitize(request.resourceName());
    this.error =
        new Error(
            Error.ErrorType.ENVIRONMENT_ISSUE,
            DiagnosticSanitizer.sanitize(message),
            DiagnosticSanitizer.sanitize(correctiveAction));
  }

  public String providerId() {
    return providerId;
  }

  public String resourceName() {
    return resourceName;
  }

  public Error error() {
    return error;
  }

  private static Throwable sanitizedCause(Throwable cause) {
    return cause == null
        ? null
        : new IllegalStateException(DiagnosticSanitizer.sanitize(cause.getMessage()));
  }
}
