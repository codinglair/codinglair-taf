package com.codinglair.taf.mcp.prompts;

import java.util.List;

/** Versioned, secret-free MCP prompt definition. */
public record PromptDefinition(
    String schemaVersion,
    String name,
    String description,
    List<PromptArgument> arguments,
    List<PromptMessage> messages) {
  public PromptDefinition {
    if (!"1.0".equals(schemaVersion)) {
      throw new IllegalArgumentException("unsupported prompt schema version");
    }
    name = TextBounds.requirePromptName(name);
    description = TextBounds.requireText(description, "description", 1024);
    arguments = List.copyOf(arguments);
    messages = List.copyOf(messages);
    if (arguments.size() > 20 || messages.isEmpty() || messages.size() > 20) {
      throw new IllegalArgumentException("prompt collection bounds exceeded");
    }
    TextBounds.rejectSecretMaterial(description);
  }
}
