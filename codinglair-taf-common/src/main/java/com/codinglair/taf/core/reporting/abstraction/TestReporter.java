package com.codinglair.taf.core.reporting.abstraction;

/** Abstraction for test reporters. */
public interface TestReporter {

  /**
   * Sets a description for the reporter.
   *
   * @param description The description
   */
  void setDescription(String description);

  /**
   * Logs a step.
   *
   * @param message The message
   */
  void logStep(String message);

  /**
   * Logs information.
   *
   * @param message The message
   */
  void logInfo(String message);

  /**
   * Attaches a screenshot.
   *
   * @param name The screenshot name
   * @param screenshot The screenshot bytes
   */
  void attachScreenshot(String name, byte[] screenshot);

  /**
   * Logs an error.
   *
   * @param message The error message
   * @param throwable The exception (optional)
   */
  void logError(String message, Throwable throwable);
}
