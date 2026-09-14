package com.codinglair.taf.messaging.aws.sqs;

import java.util.Optional;

/** Approximate source and optional dead-letter queue diagnostics. */
public record SqsQueueDiagnostics(SqsQueueCounts source, Optional<SqsQueueCounts> deadLetterQueue) {
  public SqsQueueDiagnostics {
    deadLetterQueue = deadLetterQueue == null ? Optional.empty() : deadLetterQueue;
  }
}
