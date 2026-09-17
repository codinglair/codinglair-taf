package com.codinglair.taf.mcp.tools;

import java.util.List;
import java.util.Map;

/** Strict conversion of MCP JSON arguments into the blueprint composition contract. */
public final class BlueprintRequestArguments {
  private BlueprintRequestArguments() {}

  public static BlueprintCompositionEngine.Request blueprint(Map<String, Object> arguments) {
    return BlueprintScaffoldRequest.blueprint(
        string(arguments, "groupId", true),
        string(arguments, "artifactId", true),
        string(arguments, "basePackage", true),
        string(arguments, "tafVersion", true),
        strings(arguments, "capabilities"),
        string(arguments, "messagingProvider", false),
        string(arguments, "mobilePlatform", false),
        string(arguments, "mobileAutomationName", false),
        string(arguments, "runner", false),
        string(arguments, "reporting", false),
        string(arguments, "testDefinitions", false));
  }

  public static String string(Map<String, Object> arguments, String name, boolean required) {
    Object value = arguments.get(name);
    if (value == null && !required) return null;
    if (!(value instanceof String text) || text.isBlank())
      throw new IllegalArgumentException(name + " must be a non-blank string");
    return text;
  }

  public static boolean bool(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    if (value == null) return false;
    if (value instanceof Boolean flag) return flag;
    throw new IllegalArgumentException(name + " must be a boolean");
  }

  private static List<String> strings(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    if (!(value instanceof List<?> entries)
        || entries.isEmpty()
        || entries.stream().anyMatch(entry -> !(entry instanceof String text) || text.isBlank()))
      throw new IllegalArgumentException(name + " must be a non-empty string array");
    return entries.stream().map(String.class::cast).toList();
  }
}
