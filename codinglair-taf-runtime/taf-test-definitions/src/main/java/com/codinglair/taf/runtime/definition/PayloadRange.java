package com.codinglair.taf.runtime.definition;

/** Inclusive byte range. */
public record PayloadRange(long start, long endInclusive) {
  public PayloadRange {
    if (start < 0 || (endInclusive < start && !(start == 0 && endInclusive == -1)))
      throw new IllegalArgumentException("invalid payload range");
  }

  public long length() {
    if (endInclusive == -1) return 0;
    return Math.addExact(Math.subtractExact(endInclusive, start), 1);
  }

  public static PayloadRange empty() {
    return new PayloadRange(0, -1);
  }

  public static PayloadRange all(long size) {
    if (size <= 0) throw new IllegalArgumentException("an empty payload has no byte range");
    return new PayloadRange(0, size - 1);
  }
}
