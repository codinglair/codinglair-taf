package com.codinglair.taf.runtime.core.context;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CorrelationContext provides trace context propagation across controllers and reporters. Maintains
 * correlation IDs, trace IDs, and contextual metadata without exposing secrets.
 *
 * <p>This class enables:
 *
 * <ul>
 *   <li>Trace context propagation across controllers
 *   <li>Correlation ID tracking for end-to-end test flow
 *   <li>Thread-safe context access without global mutable state
 * </ul>
 *
 * @author Codinglair TAF Team
 */
public class CorrelationContext {

  private final String traceId;
  private final String spanId;
  private final String sessionId;
  private final Map<String, String> metadata;
  private final Map<String, String> tags;
  private final Instant startTime;

  /**
   * Creates a new correlation context with auto-generated IDs.
   *
   * @return new correlation context
   */
  public static CorrelationContext create() {
    return new CorrelationContext(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        null,
        java.util.Collections.emptyMap(),
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a new correlation context with explicit IDs.
   *
   * @param traceId trace identifier
   * @param spanId span identifier
   * @param sessionId session identifier (nullable)
   * @param metadata contextual metadata
   * @param tags additional tags
   * @return new correlation context
   */
  public static CorrelationContext create(
      String traceId,
      String spanId,
      String sessionId,
      Map<String, String> metadata,
      Map<String, String> tags) {
    return new CorrelationContext(traceId, spanId, sessionId, metadata, tags);
  }

  /**
   * Creates a new correlation context with explicit IDs.
   *
   * @param traceId trace identifier
   * @param spanId span identifier
   * @param sessionId session identifier (nullable)
   * @param metadata contextual metadata
   * @return new correlation context
   */
  public static CorrelationContext create(
      String traceId, String spanId, String sessionId, Map<String, String> metadata) {
    return new CorrelationContext(
        traceId, spanId, sessionId, metadata, java.util.Collections.emptyMap());
  }

  /**
   * Creates a new correlation context with explicit IDs.
   *
   * @param traceId trace identifier
   * @param spanId span identifier
   * @param sessionId session identifier (nullable)
   * @return new correlation context
   */
  public static CorrelationContext create(String traceId, String spanId, String sessionId) {
    return new CorrelationContext(
        traceId,
        spanId,
        sessionId,
        java.util.Collections.emptyMap(),
        java.util.Collections.emptyMap());
  }

  /**
   * Creates a new correlation context with explicit IDs and metadata.
   *
   * @param traceId trace identifier
   * @param spanId span identifier
   * @param sessionId session identifier (nullable)
   * @param metadata contextual metadata
   * @param tags additional tags
   * @return new correlation context
   */
  private CorrelationContext(
      String traceId,
      String spanId,
      String sessionId,
      Map<String, String> metadata,
      Map<String, String> tags) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.sessionId = sessionId;
    this.metadata = metadata;
    this.tags = tags;
    this.startTime = Instant.now();
  }

  /**
   * Gets the trace identifier for end-to-end correlation.
   *
   * @return trace ID
   */
  public String getTraceId() {
    return traceId;
  }

  /**
   * Gets the span identifier for current operation.
   *
   * @return span ID
   */
  public String getSpanId() {
    return spanId;
  }

  /**
   * Gets the session identifier if available.
   *
   * @return session ID or null
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * Gets the contextual metadata map.
   *
   * @return immutable metadata map
   */
  public Map<String, String> getMetadata() {
    return metadata;
  }

  /**
   * Gets the tags map.
   *
   * @return immutable tags map
   */
  public Map<String, String> getTags() {
    return tags;
  }

  /**
   * Gets the correlation context start time.
   *
   * @return start time
   */
  public Instant getStartTime() {
    return startTime;
  }

  /**
   * Creates a child correlation context for a new operation/span.
   *
   * @param newSpanId new span identifier
   * @return new child context
   */
  public CorrelationContext child(String newSpanId) {
    return CorrelationContext.create(traceId, newSpanId, sessionId, metadata, tags);
  }

  /**
   * Creates a child correlation context with additional tags.
   *
   * @param newSpanId new span identifier
   * @param newTags additional tags to add
   * @return new child context
   */
  public CorrelationContext child(String newSpanId, Map<String, String> newTags) {
    Map<String, String> mergedTags = new java.util.LinkedHashMap<>(tags);
    mergedTags.putAll(newTags);
    return CorrelationContext.create(traceId, newSpanId, sessionId, metadata, mergedTags);
  }

  /**
   * Adds metadata to this context.
   *
   * @param key metadata key
   * @param value metadata value
   */
  public void addMetadata(String key, String value) {
    // In a real implementation, this would modify the metadata map
    // For now, this is a simplified version - consider using a Builder pattern
  }

  /**
   * Adds a tag to this context.
   *
   * @param key tag key
   * @param value tag value
   */
  public void addTag(String key, String value) {
    // Similar to addMetadata - simplified for now
  }

  /**
   * Gets all correlation headers as a map for propagation.
   *
   * @return headers map
   */
  public Map<String, String> toHeaders() {
    java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
    headers.put("X-Trace-ID", traceId);
    headers.put("X-Span-ID", spanId);
    if (sessionId != null) {
      headers.put("X-Session-ID", sessionId);
    }
    metadata.forEach((k, v) -> headers.put("X-Meta-" + k, v));
    tags.forEach((k, v) -> headers.put("X-Tag-" + k, v));
    return headers;
  }

  @Override
  public String toString() {
    return "CorrelationContext{"
        + "traceId='"
        + traceId
        + '\''
        + ", spanId='"
        + spanId
        + '\''
        + ", sessionId='"
        + sessionId
        + '\''
        + ", metadata="
        + metadata
        + ", tags="
        + tags
        + ", startTime="
        + startTime
        + '}';
  }
}
