package com.codinglair.taf.runtime.core.reporting.impl.allure;

/** Actionable failure raised while packaging finalized Allure results. */
public final class AllureSingleFilePublicationException extends RuntimeException {
  private final String stage;

  AllureSingleFilePublicationException(String stage, String message) {
    super("Allure single-file publication failed at " + stage + ": " + message);
    this.stage = stage;
  }

  AllureSingleFilePublicationException(String stage, String message, Throwable cause) {
    super("Allure single-file publication failed at " + stage + ": " + message, cause);
    this.stage = stage;
  }

  public String stage() {
    return stage;
  }
}
