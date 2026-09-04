package com.codinglair.taf.runtime.file;

/** Structured, path-safe failure raised at the file validation boundary. */
public final class FileValidationException extends RuntimeException {
  public enum Kind {
    PATH_SAFETY,
    MISSING,
    OVERSIZED,
    INVALID_FORMAT,
    IO,
    LIFECYCLE
  }

  private final Kind kind;
  private final String operation;
  private final String correctiveAction;

  FileValidationException(Kind kind, String operation, String correctiveAction, Throwable cause) {
    super("File validation " + operation + " failed; " + correctiveAction, cause);
    this.kind = kind;
    this.operation = operation;
    this.correctiveAction = correctiveAction;
  }

  public Kind kind() {
    return kind;
  }

  public String operation() {
    return operation;
  }

  public String correctiveAction() {
    return correctiveAction;
  }
}
