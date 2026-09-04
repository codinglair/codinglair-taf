package com.codinglair.taf.mcp.prompts;

/** A bounded input declared by a reusable prompt. */
public record PromptArgument(String name, boolean required) {
  public PromptArgument {
    name = TextBounds.requireIdentifier(name, "name");
  }
}
