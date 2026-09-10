package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.runtime.environment.DiagnosticSanitizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Common sanitization and UTF-8 byte-size enforcement before evidence publication. */
public final class AwsEvidenceSanitizer {
  private AwsEvidenceSanitizer() {}

  public static Map<String, String> attributes(Map<String, String> attributes) {
    return DiagnosticSanitizer.sanitize(attributes == null ? Map.of() : attributes);
  }

  public static AwsEvidencePayload payload(String value, int maximumBytes) {
    if (maximumBytes < 0) throw new IllegalArgumentException("maximumBytes must not be negative");
    String sanitized = DiagnosticSanitizer.sanitize(value);
    byte[] bytes = sanitized.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= maximumBytes)
      return new AwsEvidencePayload(sanitized, digest(bytes), bytes.length, false);
    int end = 0;
    while (end < sanitized.length()
        && sanitized.substring(0, end + 1).getBytes(StandardCharsets.UTF_8).length <= maximumBytes)
      end++;
    return new AwsEvidencePayload(sanitized.substring(0, end), digest(bytes), bytes.length, true);
  }

  private static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
