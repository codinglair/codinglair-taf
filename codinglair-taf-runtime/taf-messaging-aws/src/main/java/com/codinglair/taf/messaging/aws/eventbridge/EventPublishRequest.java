package com.codinglair.taf.messaging.aws.eventbridge;

import java.util.List;
import java.util.Map;

public record EventPublishRequest(
    String source,
    String detailType,
    String detail,
    List<String> resources,
    Map<String, String> metadata,
    String correlationId,
    String traceHeader) {
  public EventPublishRequest {
    resources = List.copyOf(resources == null ? List.of() : resources);
    metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
  }

  /** Source-compatible constructor for the original AWS controller contract. */
  public EventPublishRequest(
      String source,
      String detailType,
      String detail,
      Map<String, String> metadata,
      String correlationId) {
    this(source, detailType, detail, List.of(), metadata, correlationId, null);
  }
}
