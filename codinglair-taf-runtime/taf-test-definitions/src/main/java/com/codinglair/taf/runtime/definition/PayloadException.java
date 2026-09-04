package com.codinglair.taf.runtime.definition;

/** Sanitized payload-resolution failure that never exposes payload bytes or physical locations. */
public final class PayloadException extends RuntimeException {
  public enum Kind {
    MISSING,
    PATH_SAFETY,
    CHECKSUM,
    MEDIA_TYPE,
    OVERSIZED,
    RANGE,
    IO
  }

  private final Kind kind;
  private final String logicalId;

  public PayloadException(Kind kind, String logicalId, String message) {
    super(message);
    this.kind = kind;
    this.logicalId = logicalId;
  }

  public Kind kind() {
    return kind;
  }

  public String logicalId() {
    return logicalId;
  }
}
