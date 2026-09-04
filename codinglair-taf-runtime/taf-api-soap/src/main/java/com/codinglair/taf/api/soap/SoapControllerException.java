package com.codinglair.taf.api.soap;

public final class SoapControllerException extends RuntimeException {
  private final String operation;
  private final String correctiveAction;

  SoapControllerException(String operation, String correctiveAction, Throwable cause) {
    super("SOAP operation '" + operation + "' failed; " + correctiveAction, cause);
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
