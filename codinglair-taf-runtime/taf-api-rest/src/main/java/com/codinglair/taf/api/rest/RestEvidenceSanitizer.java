package com.codinglair.taf.api.rest;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** REST-specific sanitization applied before exchange evidence reaches the collector. */
final class RestEvidenceSanitizer {
  private static final String REDACTED = "[REDACTED]";
  private static final Set<String> SENSITIVE_NAMES =
      Set.of(
          "authorization",
          "proxy-authorization",
          "cookie",
          "set-cookie",
          "x-api-key",
          "api-key",
          "apikey",
          "password",
          "secret",
          "token",
          "access-token",
          "refresh-token",
          "credential");
  private static final Pattern JSON_SECRET =
      Pattern.compile(
          "(?i)(\\\"[^\\\"\\r\\n]*(?:password|passphrase|token|api[_. -]?key|secret|credential|authorization|cookie)[^\\\"\\r\\n]*\\\"\\s*:\\s*)(\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|[^,}\\r\\n]+)");
  private static final Pattern FORM_SECRET =
      Pattern.compile(
          "(?i)(^|[&;\\s])([^=&;\\s]*(?:password|token|api[_-]?key|secret|credential|authorization)[^=&;\\s]*=)[^&;\\s]*");

  private RestEvidenceSanitizer() {}

  static Map<String, Object> namedValues(Map<String, ?> source) {
    Map<String, Object> safe = new LinkedHashMap<>();
    source.forEach(
        (name, value) -> safe.put(name, isSensitive(name) ? REDACTED : sanitizeText(value)));
    return Map.copyOf(safe);
  }

  static String body(byte[] bytes, String contentType, int limit) {
    if (bytes.length == 0) return "";
    if (!isText(contentType)) return "[BINARY OMITTED; originalSize=" + bytes.length + "]";
    String sanitized = sanitizeText(new String(bytes, StandardCharsets.UTF_8));
    byte[] encoded = sanitized.getBytes(StandardCharsets.UTF_8);
    if (encoded.length <= limit) return sanitized;
    int end = Math.min(limit, sanitized.length());
    return sanitized.substring(0, end)
        + "\n[TRUNCATED after sanitization; originalSize="
        + bytes.length
        + "]";
  }

  static String sanitizeText(Object value) {
    String text = String.valueOf(value);
    text = text.replaceAll("(?i)(https?://)[^\\s/@]+:[^\\s/@]+@", "$1" + REDACTED + "@");
    text = JSON_SECRET.matcher(text).replaceAll("$1\\\"" + REDACTED + "\\\"");
    return FORM_SECRET.matcher(text).replaceAll("$1$2" + REDACTED);
  }

  static boolean isSensitive(String name) {
    String normalized = name.toLowerCase(Locale.ROOT);
    return SENSITIVE_NAMES.contains(normalized)
        || normalized.contains("password")
        || normalized.contains("secret")
        || normalized.contains("token")
        || normalized.contains("credential");
  }

  private static boolean isText(String contentType) {
    if (contentType == null) return false;
    String normalized = contentType.toLowerCase(Locale.ROOT);
    return normalized.startsWith("text/")
        || normalized.contains("json")
        || normalized.contains("xml")
        || normalized.contains("form");
  }
}
