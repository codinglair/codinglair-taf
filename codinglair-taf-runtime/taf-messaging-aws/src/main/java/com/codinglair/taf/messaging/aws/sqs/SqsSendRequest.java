package com.codinglair.taf.messaging.aws.sqs;

import java.util.Map;
import java.util.Objects;

public record SqsSendRequest(String body, Map<String, String> attributes, String correlationId) {
  public SqsSendRequest {
    Objects.requireNonNull(body, "body");
    attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
  }
}
