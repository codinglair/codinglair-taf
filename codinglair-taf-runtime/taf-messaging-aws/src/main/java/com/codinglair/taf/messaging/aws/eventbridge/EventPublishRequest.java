package com.codinglair.taf.messaging.aws.eventbridge;

import java.util.Map;

public record EventPublishRequest(
    String source,
    String detailType,
    String detail,
    Map<String, String> metadata,
    String correlationId) {
  public EventPublishRequest {
    metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
  }
}
