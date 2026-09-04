package com.codinglair.taf.runtime.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/** Opaque alias for a secret; this type never contains or resolves credential values. */
public record SecretReference(String alias) {
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public SecretReference {
    alias = Objects.requireNonNull(alias, "alias").trim();
    if (alias.isEmpty() || alias.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException(
          "Secret reference alias must be non-blank and contain no whitespace");
    }
  }

  public static SecretReference requireApproved(String value) {
    SecretReference reference = new SecretReference(value);
    if (value.startsWith("credential://")) {
      if (!value.matches("credential://[a-z0-9][a-z0-9._/-]{0,255}")) {
        throw new IllegalArgumentException("Malformed credential reference");
      }
      return reference;
    }
    if (!value.matches("secret://env/[A-Z_][A-Z0-9_]{0,127}")
        && !value.matches("secret://jasypt/[A-Za-z0-9+/=_-]{16,4000}")) {
      throw new IllegalArgumentException("Unsupported secret reference");
    }
    return reference;
  }

  @Override
  @JsonValue
  public String alias() {
    return alias;
  }
}
