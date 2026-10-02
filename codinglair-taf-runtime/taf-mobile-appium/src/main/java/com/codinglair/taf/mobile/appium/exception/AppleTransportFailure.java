package com.codinglair.taf.mobile.appium.exception;

/** Sanitized failure with only the remote-ownership uncertainty needed for scoped cleanup. */
public final class AppleTransportFailure extends IllegalStateException {
  private final boolean connectionAttempted;

  public AppleTransportFailure(boolean connectionAttempted) {
    super("Apple transport failed safely");
    this.connectionAttempted = connectionAttempted;
  }

  public boolean connectionAttempted() {
    return connectionAttempted;
  }
}
