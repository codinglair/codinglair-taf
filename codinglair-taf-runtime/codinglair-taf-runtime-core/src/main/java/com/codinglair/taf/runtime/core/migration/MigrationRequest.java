package com.codinglair.taf.runtime.core.migration;

import java.util.List;
import java.util.Objects;

/** Sanitized, engine-neutral request for one named migration history. */
public record MigrationRequest(
    String targetName,
    String technology,
    MigrationTarget target,
    MigrationPolicy policy,
    List<String> locations) {
  public MigrationRequest {
    targetName = require(targetName, "targetName");
    technology = require(technology, "technology");
    target = Objects.requireNonNull(target, "target");
    policy = Objects.requireNonNull(policy, "policy");
    locations = List.copyOf(Objects.requireNonNull(locations, "locations"));
    if (policy != MigrationPolicy.DISABLED && locations.isEmpty())
      throw new IllegalArgumentException(
          "Migration locations are required when migration is enabled");
    if (locations.stream().anyMatch(value -> value == null || value.isBlank()))
      throw new IllegalArgumentException("Migration locations must not be blank");
  }

  private static String require(String value, String field) {
    if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r"))
      throw new IllegalArgumentException(field + " is invalid");
    return value;
  }
}
