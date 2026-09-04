package com.codinglair.taf.runtime.core.context;

import com.codinglair.taf.runtime.core.precondition.PreconditionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TestContext provides the transient test execution context within a TestSession.
 *
 * <p>Maintains:
 *
 * <ul>
 *   <li>Correlation context for trace propagation
 *   <li>Precondition results for dependency-aware preconditions
 *   <li>Test execution metadata
 * </ul>
 *
 * <p>This class ensures parallel isolation by being per-session as required by FR-EX-004.
 *
 * @author Codinglair TAF Team
 */
public class TestContext {

  private final String contextId;
  private final CorrelationContext correlationContext;
  private final Map<String, PreconditionResult> preconditions;
  private final Map<String, Object> attributes;
  private final Instant startTime;
  private Instant endTime;

  /**
   * Creates a new test context with auto-generated IDs.
   *
   * @return new test context
   */
  public static TestContext create() {
    return new TestContext(
        UUID.randomUUID().toString(),
        CorrelationContext.create(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        Instant.now(),
        null);
  }

  /**
   * Creates a new test context with explicit correlation context.
   *
   * @param correlationContext correlation context to propagate
   * @return new test context
   */
  public static TestContext create(CorrelationContext correlationContext) {
    return new TestContext(
        UUID.randomUUID().toString(),
        correlationContext,
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        Instant.now(),
        null);
  }

  /**
   * Creates a new test context with explicit parameters.
   *
   * @param contextId context identifier
   * @param correlationContext correlation context
   * @param preconditions precondition results map
   * @param attributes context attributes
   * @param startTime test start time
   * @param endTime test end time (nullable)
   * @return new test context
   */
  private TestContext(
      String contextId,
      CorrelationContext correlationContext,
      Map<String, PreconditionResult> preconditions,
      Map<String, Object> attributes,
      Instant startTime,
      Instant endTime) {
    this.contextId = contextId;
    this.correlationContext = correlationContext;
    this.preconditions = preconditions;
    this.attributes = attributes;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  /**
   * Gets the context identifier.
   *
   * @return context ID
   */
  public String getContextId() {
    return contextId;
  }

  /**
   * Gets the correlation context for trace propagation.
   *
   * @return correlation context
   */
  public CorrelationContext getCorrelationContext() {
    return correlationContext;
  }

  /**
   * Gets precondition results.
   *
   * @return immutable map of precondition results
   */
  public Map<String, PreconditionResult> getPreconditions() {
    return preconditions;
  }

  /**
   * Checks if a precondition was checked.
   *
   * @param key precondition key
   * @return true if checked, false otherwise
   */
  public boolean hasPrecondition(String key) {
    return preconditions.containsKey(key);
  }

  /**
   * Gets a precondition result by key.
   *
   * @param key precondition key
   * @return precondition result, or null if not checked
   */
  public PreconditionResult getPrecondition(String key) {
    return preconditions.get(key);
  }

  /**
   * Registers a precondition result.
   *
   * @param key precondition key
   * @param result precondition result
   */
  public void registerPrecondition(String key, PreconditionResult result) {
    preconditions.put(key, result);
  }

  /**
   * Gets context attributes.
   *
   * @return immutable attributes map
   */
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  /**
   * Sets an attribute.
   *
   * @param key attribute key
   * @param value attribute value
   */
  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  /**
   * Gets the test start time.
   *
   * @return start time
   */
  public Instant getStartTime() {
    return startTime;
  }

  /**
   * Gets the test end time if completed.
   *
   * @return end time or null if not completed
   */
  public Instant getEndTime() {
    return endTime;
  }

  /**
   * Completes the test context.
   *
   * @param endTime test end time
   */
  public void complete(Instant endTime) {
    this.endTime = endTime;
  }

  /**
   * Checks if the test context is completed.
   *
   * @return true if completed
   */
  public boolean isCompleted() {
    return endTime != null;
  }

  /**
   * Creates a child context for a nested operation.
   *
   * @return new child context
   */
  public TestContext child() {
    return new TestContext(
        UUID.randomUUID().toString(),
        correlationContext.child("child-" + System.currentTimeMillis()),
        new ConcurrentHashMap<>(preconditions),
        new ConcurrentHashMap<>(attributes),
        startTime,
        null);
  }

  /**
   * Creates a child context with explicit correlation span.
   *
   * @param spanId new span ID
   * @return new child context
   */
  public TestContext child(String spanId) {
    CorrelationContext childContext = correlationContext.child(spanId);
    return new TestContext(
        UUID.randomUUID().toString(),
        childContext,
        new ConcurrentHashMap<>(preconditions),
        new ConcurrentHashMap<>(attributes),
        startTime,
        null);
  }

  /**
   * Gets context as correlation headers for propagation.
   *
   * @return headers map
   */
  public Map<String, String> toHeaders() {
    return correlationContext.toHeaders();
  }

  /**
   * Gets all precondition keys.
   *
   * @return list of precondition keys
   */
  public List<String> getPreconditionKeys() {
    return new ArrayList<>(preconditions.keySet());
  }

  /**
   * Gets failed precondition keys.
   *
   * @return list of failed precondition keys
   */
  public List<String> getFailedPreconditionKeys() {
    List<String> failed = new ArrayList<>();
    for (Map.Entry<String, PreconditionResult> entry : preconditions.entrySet()) {
      if (entry.getValue().getStatus() == PreconditionResult.PreconditionStatus.FAILED) {
        failed.add(entry.getKey());
      }
    }
    return failed;
  }

  /**
   * Checks if any precondition failed.
   *
   * @return true if any precondition failed
   */
  public boolean hasFailedPreconditions() {
    return !getFailedPreconditionKeys().isEmpty();
  }

  @Override
  public String toString() {
    return "TestContext{"
        + "contextId='"
        + contextId
        + '\''
        + ", correlationContext="
        + correlationContext
        + ", preconditions="
        + preconditions
        + ", attributes="
        + attributes
        + ", startTime="
        + startTime
        + ", endTime="
        + endTime
        + '}';
  }
}
