package com.codinglair.taf.messaging.aws.eventbridge;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record EventPublishResult(
    List<EventPublishEntryResult> entries,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<EventPublishEvidence> requestEvidence,
    Instant publishedAt) {
  public EventPublishResult {
    entries = List.copyOf(entries);
    requestEvidence = List.copyOf(requestEvidence == null ? List.of() : requestEvidence);
  }

  /** Source-compatible constructor for the original AWS controller contract. */
  public EventPublishResult(List<EventPublishEntryResult> entries, Instant publishedAt) {
    this(entries, List.of(), publishedAt);
  }
}
