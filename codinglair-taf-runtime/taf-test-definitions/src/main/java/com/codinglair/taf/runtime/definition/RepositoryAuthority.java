package com.codinglair.taf.runtime.definition;

import java.util.Objects;

/** Stable identity of the provider authorized to write a project's definitions. */
public record RepositoryAuthority(String id) {
  public RepositoryAuthority {
    id = Objects.requireNonNull(id, "id").trim().toLowerCase(java.util.Locale.ROOT);
    if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
      throw new IllegalArgumentException("authority id is invalid");
    }
  }
}
