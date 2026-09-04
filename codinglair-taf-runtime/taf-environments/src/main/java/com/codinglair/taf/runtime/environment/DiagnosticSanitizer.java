package com.codinglair.taf.runtime.environment;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Sanitizes diagnostic metadata before it crosses the provider boundary. */
public final class DiagnosticSanitizer {
  public static final String REDACTED = "[REDACTED]";
  private static final Set<String> SENSITIVE_KEYS =
      Set.of(
          "authorization",
          "cookie",
          "credential",
          "password",
          "secret",
          "token",
          "apikey",
          "api_key");
  private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]+");
  private static final Pattern ASSIGNMENT =
      Pattern.compile("(?i)(password|secret|token|api[_-]?key|authorization)\\s*[:=]\\s*[^,;\\s]+");

  private DiagnosticSanitizer() {}

  public static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return ASSIGNMENT
        .matcher(BEARER.matcher(value).replaceAll(REDACTED))
        .replaceAll("$1=" + REDACTED);
  }

  public static Map<String, String> sanitize(Map<String, String> values) {
    var sanitized = new LinkedHashMap<String, String>();
    values.forEach(
        (key, value) -> sanitized.put(key, isSensitive(key) ? REDACTED : sanitize(value)));
    return Map.copyOf(sanitized);
  }

  private static boolean isSensitive(String key) {
    String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace(".", "");
    return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
  }
}
