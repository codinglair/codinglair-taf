package com.codinglair.taf.core.controller.abstraction;

import com.codinglair.taf.core.environment.EnvironmentProperties;

/**
 * Main Controller interface. Consider it as an actor performing any testing
 * actions. Interact with a browser, connect to JMS or Kafka, etc.
 * Corresponding implementation should be provided.
 */
public interface TestController {
    void setup(EnvironmentProperties envProps);    // Start browser, connect to Kafka, etc.
    void teardown(); // Close browser, disconnect, etc.

    default byte[] getFailureAttachment() { return null; }
    default String getAttachmentType() { return "text/plain"; }
    default String getAttachmentExtension() { return ".txt"; }
}
