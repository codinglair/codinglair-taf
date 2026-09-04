package com.codinglair.taf.mcp.jobs;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable durable job snapshot. */
public record Job(
    JobId id,
    String operation,
    Map<String, String> payload,
    JobState state,
    int progressPercent,
    Optional<String> safeCheckpoint,
    List<JobReference> resultReferences,
    List<JobEvent> events,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  public Job {
    Objects.requireNonNull(id, "id");
    operation = text(operation, "operation", 64);
    payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    Objects.requireNonNull(state, "state");
    if (progressPercent < 0 || progressPercent > 100) {
      throw new IllegalArgumentException("Progress must be between 0 and 100");
    }
    safeCheckpoint =
        Objects.requireNonNull(safeCheckpoint, "safeCheckpoint")
            .map(value -> text(value, "safeCheckpoint", 256));
    resultReferences = List.copyOf(Objects.requireNonNull(resultReferences, "resultReferences"));
    events = List.copyOf(Objects.requireNonNull(events, "events"));
    if (version < 0) {
      throw new IllegalArgumentException("Version cannot be negative");
    }
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static Job queued(JobId id, String operation, Map<String, String> payload, Instant now) {
    return new Job(
        id,
        operation,
        payload,
        JobState.QUEUED,
        0,
        Optional.empty(),
        List.of(),
        List.of(),
        0,
        now,
        now);
  }

  static String text(String value, String name, int maximum) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()
        || value.length() > maximum
        || value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(
          name + " must be nonblank, bounded, and free of control characters");
    }
    return value;
  }
}
