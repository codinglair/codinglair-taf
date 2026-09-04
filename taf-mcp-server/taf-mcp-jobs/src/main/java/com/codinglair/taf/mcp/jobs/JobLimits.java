package com.codinglair.taf.mcp.jobs;

/** Administrative persistence bounds. */
public record JobLimits(
    int maximumPayloadEntries,
    int maximumPayloadCharacters,
    int maximumEvents,
    int maximumReferences) {
  public static final JobLimits DEFAULTS = new JobLimits(32, 8192, 1000, 100);

  public JobLimits {
    if (maximumPayloadEntries < 1
        || maximumPayloadCharacters < 1
        || maximumEvents < 1
        || maximumReferences < 1) {
      throw new IllegalArgumentException("All job limits must be positive");
    }
  }
}
