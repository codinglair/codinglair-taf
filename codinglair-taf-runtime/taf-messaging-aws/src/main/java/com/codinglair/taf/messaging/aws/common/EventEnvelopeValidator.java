package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.messaging.aws.eventbridge.EventPublishRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Package-private JSON construction and configured schema-validation boundary. */
final class EventEnvelopeValidator {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final JsonSchema schema;

  EventEnvelopeValidator(String configuredSchema) {
    if (configuredSchema == null || configuredSchema.isBlank()) {
      schema = null;
      return;
    }
    try {
      schema =
          JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
              .getSchema(JSON.readTree(configuredSchema));
    } catch (IOException | RuntimeException failure) {
      throw new IllegalArgumentException(
          "Configured EventBridge envelope schema is invalid", failure);
    }
  }

  String detail(EventPublishRequest event) {
    validateRequired(event);
    try {
      JsonNode parsed = JSON.readTree(event.detail());
      if (!(parsed instanceof ObjectNode detail))
        throw new IllegalArgumentException("EventBridge detail must be a JSON object");
      if (event.correlationId() != null || !event.metadata().isEmpty()) {
        ObjectNode taf = detail.withObject("_taf");
        if (event.correlationId() != null) taf.put("correlationId", event.correlationId());
        ObjectNode metadata = taf.withObject("metadata");
        event.metadata().forEach(metadata::put);
      }
      return JSON.writeValueAsString(detail);
    } catch (IOException failure) {
      throw new IllegalArgumentException("EventBridge detail is not valid JSON", failure);
    }
  }

  String sanitizedDetail(String detail) {
    try {
      JsonNode value = JSON.readTree(detail);
      sanitize(value);
      return JSON.writeValueAsString(value);
    } catch (IOException failure) {
      throw new IllegalArgumentException("EventBridge detail is not valid JSON", failure);
    }
  }

  JsonNode validateTargetEnvelope(String envelope, String correlationId) {
    try {
      JsonNode value = JSON.readTree(envelope);
      if (!value.isObject())
        throw new AssertionError("SQS target did not contain an EventBridge envelope");
      if (schema != null) {
        var errors = schema.validate(value);
        if (!errors.isEmpty())
          throw new AssertionError(
              "EventBridge envelope schema validation failed with "
                  + errors.size()
                  + " violation(s)");
      }
      String actual = value.path("detail").path("_taf").path("correlationId").textValue();
      if (correlationId != null && !correlationId.equals(actual))
        throw new AssertionError("EventBridge target envelope correlation identity did not match");
      return value;
    } catch (IOException failure) {
      throw new AssertionError("SQS target did not contain valid JSON", failure);
    }
  }

  void assertExpected(JsonNode envelope, EventPublishRequest expected) {
    if (!expected.source().equals(envelope.path("source").textValue()))
      throw new AssertionError("EventBridge target envelope source did not match");
    if (!expected.detailType().equals(envelope.path("detail-type").textValue()))
      throw new AssertionError("EventBridge target envelope detail type did not match");
    for (String resource : expected.resources())
      if (!contains(envelope.path("resources"), resource))
        throw new AssertionError("EventBridge target envelope resources did not match");
  }

  private static boolean contains(JsonNode values, String expected) {
    for (JsonNode value : values) if (expected.equals(value.textValue())) return true;
    return false;
  }

  private static void sanitize(JsonNode value) {
    if (value instanceof ObjectNode object) {
      List<String> names = new ArrayList<>();
      object.fieldNames().forEachRemaining(names::add);
      for (String name : names) {
        JsonNode child = object.get(name);
        if (child.isTextual()) {
          String sanitized =
              AwsEvidenceSanitizer.attributes(Map.of(name, child.textValue())).get(name);
          object.put(name, sanitized);
        } else {
          sanitize(child);
        }
      }
    } else if (value.isArray()) {
      value.forEach(EventEnvelopeValidator::sanitize);
    }
  }

  private static void validateRequired(EventPublishRequest event) {
    if (event == null) throw new IllegalArgumentException("event must not be null");
    if (event.source() == null || event.source().isBlank())
      throw new IllegalArgumentException("EventBridge source is required");
    if (event.detailType() == null || event.detailType().isBlank())
      throw new IllegalArgumentException("EventBridge detailType is required");
    if (event.detail() == null || event.detail().isBlank())
      throw new IllegalArgumentException("EventBridge detail is required");
    if (event.correlationId() != null && event.correlationId().isBlank())
      throw new IllegalArgumentException("EventBridge correlationId must not be blank");
    for (Map.Entry<String, String> metadata : event.metadata().entrySet())
      if (metadata.getKey() == null || metadata.getKey().isBlank() || metadata.getValue() == null)
        throw new IllegalArgumentException(
            "EventBridge metadata must contain non-blank keys and values");
  }
}
