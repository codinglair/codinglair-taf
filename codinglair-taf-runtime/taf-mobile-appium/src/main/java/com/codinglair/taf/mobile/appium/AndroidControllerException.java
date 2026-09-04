package com.codinglair.taf.mobile.appium;

public final class AndroidControllerException extends RuntimeException {
  private final String operation;
  private final String correctiveAction;

  AndroidControllerException(String operation, String correctiveAction, Throwable cause) {
    super("Android Appium operation '" + operation + "' failed; " + correctiveAction, cause);
    this.operation = operation;
    this.correctiveAction = correctiveAction;
  }

  public String operation() {
    return operation;
  }

  public String correctiveAction() {
    return correctiveAction;
  }
}
