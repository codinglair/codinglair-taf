package com.codinglair.taf.mobile.appium.exception;

/** Sanitized boundary failure; raw provider exceptions may contain credentials or payloads. */
public final class AppleControllerException extends RuntimeException {
  private final String operation;

  public AppleControllerException(String operation) {
    super(
        "Apple Appium "
            + operation
            + " failed; verify endpoint authorization, XCUITest and target configuration");
    this.operation = operation;
  }

  public String operation() {
    return operation;
  }
}
