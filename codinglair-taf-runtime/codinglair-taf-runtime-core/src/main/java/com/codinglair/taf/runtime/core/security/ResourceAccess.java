package com.codinglair.taf.runtime.core.security;

import java.util.Objects;

/** An execution-boundary request. Identifiers are policy inputs, never diagnostic output. */
public record ResourceAccess(Kind kind, String resource, String action) {
  public enum Kind {
    ENDPOINT,
    TARGET,
    APPLICATION,
    ARTIFACT_DESTINATION,
    CREDENTIAL_FORWARDING
  }

  public ResourceAccess {
    Objects.requireNonNull(kind);
    if (resource == null
        || resource.isBlank()
        || resource.length() > 16384
        || resource.chars().anyMatch(Character::isISOControl)
        || action == null
        || !action.matches("(?:\\*|[a-z][a-z0-9-]{0,63})"))
      throw new IllegalArgumentException("Invalid resource authorization request");
  }

  /** Explicit trusted grants may cover all actions on an exact resource. */
  public boolean covers(ResourceAccess request) {
    return kind == request.kind
        && resource.equals(request.resource)
        && (action.equals("*") || action.equals(request.action));
  }

  @Override
  public String toString() {
    return "ResourceAccess[kind=" + kind + ", action=" + action + "]";
  }
}
