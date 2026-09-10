package com.codinglair.taf.messaging.aws.sqs;

import java.time.Instant;
import java.util.Map;

public record SqsMessage(
    String messageId,
    String body,
    Map<String, String> attributes,
    String correlationId,
    int receiveCount,
    Instant receivedAt) {
  public SqsMessage {
    attributes = Map.copyOf(attributes);
  }
}
