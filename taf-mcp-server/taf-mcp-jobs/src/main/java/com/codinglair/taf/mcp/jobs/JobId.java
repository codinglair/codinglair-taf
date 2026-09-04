package com.codinglair.taf.mcp.jobs;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Opaque, path-safe job identifier. */
public record JobId(String value) {
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

  public JobId {
    Objects.requireNonNull(value, "value");
    if (!VALID.matcher(value).matches()) {
      throw new IllegalArgumentException("Job ID must be a path-safe MCP identifier");
    }
  }

  public static JobId create() {
    return new JobId(UUID.randomUUID().toString());
  }
}
