package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Session-scoped boundary for bounded AWS evidence entering the neutral artifact pipeline. */
final class AwsEvidencePublisher {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ArtifactCollector artifacts;
  private final int maximumBytes;
  private final AtomicLong sequence = new AtomicLong();

  AwsEvidencePublisher(ArtifactCollector artifacts, int maximumBytes) {
    this.artifacts = artifacts;
    this.maximumBytes = maximumBytes;
  }

  void publish(String service, String operation, Map<String, ?> evidence) {
    if (artifacts == null) return;
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("service", service);
    envelope.put("operation", operation);
    envelope.put("evidence", evidence);
    AwsEvidencePayload payload = AwsEvidenceSanitizer.payload(json(envelope), maximumBytes);
    Map<String, Object> published = new LinkedHashMap<>();
    published.put("content", payload.content());
    published.put("sha256", payload.sha256());
    published.put("originalBytes", payload.originalBytes());
    published.put("truncated", payload.truncated());
    artifacts.addArtifact(
        "aws-"
            + service.toLowerCase()
            + "-"
            + operation.replace(' ', '-')
            + "-"
            + sequence.incrementAndGet(),
        "AWS_EVIDENCE",
        json(published),
        "application/json",
        service + " " + operation);
  }

  void failure(String service, String operation, AwsControllerException failure) {
    publish(
        service,
        operation + "-failure",
        Map.of(
            "category", failure.category().name(),
            "retryable", failure.retryable(),
            "message", failure.getMessage()));
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Unable to serialize sanitized AWS evidence", failure);
    }
  }
}
