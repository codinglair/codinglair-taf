package com.codinglair.taf.runtime.definition;

/** Explicit optimistic-concurrency or source-authority conflict. */
public final class RepositoryConflictException extends IllegalStateException {
  public enum Kind {
    VERSION,
    AUTHORITY,
    CONCURRENT_WRITE
  }

  private final Kind kind;

  public RepositoryConflictException(Kind kind, String message) {
    super(message);
    this.kind = java.util.Objects.requireNonNull(kind, "kind");
  }

  public Kind kind() {
    return kind;
  }
}
