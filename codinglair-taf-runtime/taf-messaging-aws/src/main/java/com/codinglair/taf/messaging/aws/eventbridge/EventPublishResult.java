package com.codinglair.taf.messaging.aws.eventbridge;

import java.time.Instant;
import java.util.List;

public record EventPublishResult(List<EventPublishEntryResult> entries, Instant publishedAt) {
  public EventPublishResult {
    entries = List.copyOf(entries);
  }
}
