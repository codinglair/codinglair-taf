package com.codinglair.taf.mcp.prompts;

/** One immutable message in a reusable prompt template. */
public record PromptMessage(Role role, String text) {
  public enum Role {
    USER,
    ASSISTANT
  }

  public PromptMessage {
    if (role == null) {
      throw new IllegalArgumentException("role is required");
    }
    text = TextBounds.requireText(text, "text", 8192);
    TextBounds.rejectSecretMaterial(text);
  }
}
