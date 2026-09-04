package com.codinglair.taf.runtime.core.reporting;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * RedactionPipeline handles secret redaction and content hashing for reporting.
 *
 * <p>Ensures sensitive data is sanitized before persistence or adapter delivery.
 *
 * @see RedactionService
 */
public class RedactionPipeline implements RedactionService {

  private static final Pattern SECRET_PATTERN =
      Pattern.compile("(?i)(secret|password|token|api.key|credential|apikey)\\s*=\\s*\\S+");
  private static final Pattern IP_PATTERN =
      Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

  private final Map<String, String> secretPatterns;

  public RedactionPipeline() {
    this.secretPatterns = new HashMap<>();
    this.secretPatterns.put("password", "(?i)password\\s*=\\s*\\S+");
    this.secretPatterns.put("secret", "(?i)secret\\s*=\\s*\\S+");
    this.secretPatterns.put("token", "(?i)token\\s*=\\s*\\S+");
    this.secretPatterns.put("api.key", "(?i)api\\.key\\s*=\\s*\\S+");
    this.secretPatterns.put("apiKey", "(?i)apikey\\s*=\\s*\\S+");
    this.secretPatterns.put("credentials", "(?i)credentials\\s*=\\s*\\S+");
    this.secretPatterns.put("sessionToken", "(?i)sessionToken\\s*=\\s*\\S+");
    this.secretPatterns.put("authToken", "(?i)authToken\\s*=\\s*\\S+");
    this.secretPatterns.put("accessKey", "(?i)accessKey\\s*=\\s*\\S+");
    this.secretPatterns.put("secretKey", "(?i)secretKey\\s*=\\s*\\S+");
    this.secretPatterns.put("bearer", "(?i)bearer\\s*=\\s*\\S+");
    this.secretPatterns.put("private", "(?i)private\\s*=\\s*\\S+");
    this.secretPatterns.put("credential", "(?i)credential\\s*=\\s*\\S+");
    this.secretPatterns.put("apikey", "(?i)apikey\\s*=\\s*\\S+");
    this.secretPatterns.put("authorization", "(?i)authorization\\s*=\\s*\\S+");
  }

  /**
   * Redact sensitive information from the given content.
   *
   * @param content the content to redact
   * @return the redacted content
   */
  public String redact(String content) {
    if (content == null) {
      return null;
    }

    String redacted = content;

    // Redact known secret patterns
    for (Map.Entry<String, String> entry : secretPatterns.entrySet()) {
      redacted = redacted.replaceAll(entry.getValue(), "****");
    }

    // Redact IP addresses
    redacted = redacted.replaceAll("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}", "0.0.0.0");

    return redacted;
  }

  /**
   * Hash content using SHA-256 algorithm.
   *
   * @param content the content to hash
   * @return the hex-encoded hash
   */
  public String hash(String content) {
    if (content == null) {
      return null;
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(content.getBytes());
      return Base64.getEncoder().encodeToString(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Redact and hash content in a single operation.
   *
   * @param content the content to process
   * @return the processed content with redaction and hashing
   */
  public String redactAndHash(String content) {
    if (content == null) {
      return null;
    }

    String redacted = redact(content);
    return hash(redacted);
  }

  /**
   * Get the secret patterns used for redaction.
   *
   * @return the secret patterns
   */
  public Map<String, String> getSecretPatterns() {
    return secretPatterns;
  }
}
