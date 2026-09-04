package com.codinglair.taf.mcp.prompts;

import java.util.Locale;
import java.util.regex.Pattern;

final class TextBounds {
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9.-]{0,127}");
  private static final Pattern PROMPT_NAME = Pattern.compile("taf\\.[a-z.-]+");
  private static final Pattern SECRET =
      Pattern.compile("(?i)(password|passwd|secret|api[_-]?key|access[_-]?token)\\s*[:=]");

  private TextBounds() {}

  static String requireIdentifier(String value, String field) {
    var bounded = requireText(value, field, 128);
    if (!IDENTIFIER.matcher(bounded).matches()) {
      throw new IllegalArgumentException(field + " is invalid");
    }
    return bounded;
  }

  static String requirePromptName(String value) {
    var bounded = requireText(value, "name", 128);
    if (!PROMPT_NAME.matcher(bounded).matches()) {
      throw new IllegalArgumentException("prompt name is invalid");
    }
    return bounded;
  }

  static String requireText(String value, String field, int maximum) {
    if (value == null || value.isBlank() || value.length() > maximum) {
      throw new IllegalArgumentException(field + " is required and must be at most " + maximum);
    }
    return value;
  }

  static void rejectSecretMaterial(String value) {
    if (SECRET.matcher(value.toLowerCase(Locale.ROOT)).find()) {
      throw new IllegalArgumentException("prompt contains secret-like material");
    }
  }
}
