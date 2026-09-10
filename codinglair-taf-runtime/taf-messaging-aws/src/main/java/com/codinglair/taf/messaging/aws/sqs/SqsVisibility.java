package com.codinglair.taf.messaging.aws.sqs;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Last visibility value controlled by this session for a received message. */
public record SqsVisibility(Optional<Duration> timeout, Instant changedAt) {
  public SqsVisibility {
    timeout = timeout == null ? Optional.empty() : timeout;
  }
}
