package com.codinglair.taf.core.reporting.abstraction;

/**
 * Main interface for any reporter to be implemented in the framework.
 */
public interface TestReporter {
    void setDescription(String description);
    void logStep(String message);
    void logInfo(String message);
    void attachScreenshot(String name, byte[] screenshot);
    void logError(String message, Throwable throwable);
}
