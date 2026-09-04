package com.codinglair.taf.runtime.definition;

import java.util.Objects;

/** External schema classification for one named source field. */
public record SecretFieldDefinition(SecretKind kind, boolean required) {
  public SecretFieldDefinition {
    kind = Objects.requireNonNull(kind, "kind");
  }

  public static SecretFieldDefinition required(SecretKind kind) {
    return new SecretFieldDefinition(kind, true);
  }
}
