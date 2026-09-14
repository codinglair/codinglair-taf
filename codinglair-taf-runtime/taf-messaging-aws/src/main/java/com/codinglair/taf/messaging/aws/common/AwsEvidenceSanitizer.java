package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.runtime.environment.DiagnosticSanitizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/** Common sanitization and UTF-8 byte-size enforcement before evidence publication. */
public final class AwsEvidenceSanitizer {
  private static final Pattern JSON_SECRET =
      Pattern.compile(
          "(?i)(\"(?:password|secret|token|api[_-]?key|authorization|credential|receipt[_-]?handle)\"\\s*:\\s*\")[^\"]*(\")");
  private static final Pattern AWS_ACCESS_KEY = Pattern.compile("(?:AKIA|ASIA)[A-Z0-9]{16}");
  private static final Pattern BASIC_AUTH = Pattern.compile("(?i)(https?://)[^/@\\s]+:[^/@\\s]+@");
  private static final Pattern SENSITIVE_QUERY =
      Pattern.compile("(?i)([?&](?:token|secret|password|signature|x-amz-credential)=)[^&#\\s]+");

  private AwsEvidenceSanitizer() {}

  public static Map<String, String> attributes(Map<String, String> attributes) {
    return DiagnosticSanitizer.sanitize(attributes == null ? Map.of() : attributes);
  }

  public static AwsEvidencePayload payload(String value, int maximumBytes) {
    if (maximumBytes < 0) throw new IllegalArgumentException("maximumBytes must not be negative");
    String sanitized = sanitize(value);
    byte[] bytes = sanitized.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= maximumBytes)
      return new AwsEvidencePayload(sanitized, digest(bytes), bytes.length, false);
    int end = 0;
    while (end < sanitized.length()
        && sanitized.substring(0, end + 1).getBytes(StandardCharsets.UTF_8).length <= maximumBytes)
      end++;
    return new AwsEvidencePayload(sanitized.substring(0, end), digest(bytes), bytes.length, true);
  }

  public static String sanitize(String value) {
    String sanitized = DiagnosticSanitizer.sanitize(value);
    sanitized = JSON_SECRET.matcher(sanitized).replaceAll("$1[REDACTED]$2");
    sanitized = AWS_ACCESS_KEY.matcher(sanitized).replaceAll("[REDACTED]");
    sanitized = BASIC_AUTH.matcher(sanitized).replaceAll("$1[REDACTED]@");
    return SENSITIVE_QUERY.matcher(sanitized).replaceAll("$1[REDACTED]");
  }

  private static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
