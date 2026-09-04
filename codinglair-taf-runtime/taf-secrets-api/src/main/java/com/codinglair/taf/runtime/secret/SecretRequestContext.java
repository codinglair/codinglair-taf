package com.codinglair.taf.runtime.secret;

import java.util.Objects;

/** Non-sensitive identity and authorization decision for one deterministic request. */
public record SecretRequestContext(
    String requestingComponent, String sessionId, String environment, boolean authorized) {
  public SecretRequestContext {
    requestingComponent = requireSafe(requestingComponent, "requestingComponent");
    sessionId = requireSafe(sessionId, "sessionId");
    environment = requireSafe(environment, "environment");
  }

  private static String requireSafe(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank() || value.length() > 128 || value.contains("\n") || value.contains("\r"))
      throw new IllegalArgumentException(name + " is invalid");
    return value;
  }
}
