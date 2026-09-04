package com.codinglair.taf.core.reporting.impl;

import com.codinglair.taf.core.reporting.abstraction.TestReporter;

/** No-op reporter implementation for fallback scenarios. */
public class NoOpReporter implements TestReporter {

  @Override
  public void setDescription(String description) {
    // No-op implementation
  }

  @Override
  public void logStep(String message) {
    // No-op implementation
  }

  @Override
  public void logInfo(String message) {
    // No-op implementation
  }

  @Override
  public void attachScreenshot(String name, byte[] screenshot) {
    // No-op implementation
  }

  @Override
  public void logError(String message, Throwable throwable) {
    // No-op implementation
  }
}
