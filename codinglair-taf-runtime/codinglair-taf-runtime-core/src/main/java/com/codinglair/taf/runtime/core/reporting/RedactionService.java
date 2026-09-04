package com.codinglair.taf.runtime.core.reporting;

import java.util.Map;

/**
 * RedactionService SPI for integrating secret redaction into the TestReporter pipeline.
 *
 * <p>This service provides a controlled interface for all TestReporter implementations to use when
 * redacting sensitive data before logging, storing, or transmitting.
 *
 * @see RedactionPipeline
 */
public interface RedactionService {

  /**
   * Redact sensitive information from the given content.
   *
   * @param content the content to redact
   * @return the redacted content
   */
  String redact(String content);

  /**
   * Hash content using SHA-256 algorithm.
   *
   * @param content the content to hash
   * @return the hex-encoded hash
   */
  String hash(String content);

  /**
   * Get the secret patterns used for redaction.
   *
   * @return the secret patterns
   */
  Map<String, String> getSecretPatterns();
}
