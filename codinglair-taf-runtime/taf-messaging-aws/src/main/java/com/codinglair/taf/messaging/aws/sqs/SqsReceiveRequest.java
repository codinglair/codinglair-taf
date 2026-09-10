package com.codinglair.taf.messaging.aws.sqs;

import java.time.Duration;
import java.util.Map;

public record SqsReceiveRequest(
    Duration timeout, String correlationId, Map<String, String> attributes, int maximumMessages) {
  public SqsReceiveRequest {
    if (timeout == null || timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("timeout must be positive");
    attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    if (maximumMessages < 1 || maximumMessages > 10)
      throw new IllegalArgumentException("maximumMessages must be between 1 and 10");
  }
}
