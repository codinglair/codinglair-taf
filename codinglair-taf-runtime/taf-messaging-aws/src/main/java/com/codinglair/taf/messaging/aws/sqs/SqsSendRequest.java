package com.codinglair.taf.messaging.aws.sqs;

import java.util.Map;
import java.util.Objects;

public record SqsSendRequest(String body, Map<String, String> attributes, String correlationId) {
  public SqsSendRequest {
    Objects.requireNonNull(body, "body");
    attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    if (correlationId != null && correlationId.isBlank())
      throw new IllegalArgumentException("correlationId must not be blank");
    if (correlationId != null
        && attributes.containsKey("correlationId")
        && !correlationId.equals(attributes.get("correlationId")))
      throw new IllegalArgumentException(
          "correlationId conflicts with the correlationId attribute");
  }
}
