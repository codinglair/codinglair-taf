package com.codinglair.taf.runtime.core.reporting;

import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ArtifactCollector manages typed artifacts attached to test execution.
 *
 * <p>Supports multiple typed artifacts per step and preserves execution/rerun attempts.
 */
public class ArtifactCollector {

  private final String sessionId;
  private final String testId;
  private final TafTest test;
  private final List<TestStep> steps = new CopyOnWriteArrayList<>();
  private final List<TestArtifact> artifacts = new CopyOnWriteArrayList<>();
  private final AtomicLong sequenceNumber = new AtomicLong(0);
  private final AtomicInteger rerunAttempt = new AtomicInteger(0);
  private final java.util.concurrent.atomic.AtomicBoolean finalized =
      new java.util.concurrent.atomic.AtomicBoolean();
  private final RedactionPipeline redactionPipeline;

  public ArtifactCollector(TafTest test, String sessionId, String testId) {
    this.test = test;
    this.sessionId = sessionId;
    this.testId = testId;
    this.redactionPipeline = new RedactionPipeline();
  }

  /**
   * Add a step to the test execution.
   *
   * @param step the step to add
   * @return the collector for chaining
   */
  public ArtifactCollector addStep(TestStep step) {
    Objects.requireNonNull(step, "step");
    steps.add(TestStep.of(redact(step.name()), redact(step.status()), redact(step.description())));
    return this;
  }

  /**
   * Add an artifact to the test execution.
   *
   * @param artifact the artifact to add
   * @return the collector for chaining
   */
  public ArtifactCollector addArtifact(TestArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    addArtifact(
        artifact.name(),
        artifact.type(),
        artifact.content(),
        artifact.contentType(),
        artifact.stepName());
    return this;
  }

  /**
   * Add an artifact with SHA-256 hash and step association.
   *
   * @param name the artifact name
   * @param type the artifact type
   * @param content the artifact content
   * @param contentType the content type
   * @param stepName the associated step name
   * @return the collector for chaining
   */
  public ArtifactCollector addArtifact(
      String name, String type, String content, String contentType, String stepName) {
    String safeName = redact(name);
    String safeType = redact(type);
    String safeContent = redact(content);
    String safeContentType = redact(contentType);
    String safeStep = redact(stepName);
    String hash = computeHash(safeContent);
    TestArtifact artifact =
        TestArtifact.of(safeName, safeType, safeContent, safeContentType, safeStep, hash);
    artifacts.add(artifact);
    return this;
  }

  /** Finalizes sanitized failure evidence through the neutral reporter boundary. */
  public void finalizeEvidence(ReporterDispatcher dispatcher) {
    finalizeEvidenceForEach(List.of(Objects.requireNonNull(dispatcher, "dispatcher")));
  }

  /** Finalizes the same sanitized evidence exactly once to every configured reporter. */
  public void finalizeEvidenceForEach(Iterable<ReporterDispatcher> dispatchers) {
    Objects.requireNonNull(dispatchers, "dispatchers");
    if (!finalized.compareAndSet(false, true)) return;
    dispatchers.forEach(
        dispatcher -> {
          steps.forEach(dispatcher::reportStep);
          artifacts.forEach(dispatcher::reportArtifact);
        });
  }

  /**
   * Redact sensitive data from content.
   *
   * @param content the content to redact
   * @return the redacted content
   */
  public String redact(String content) {
    if (content == null) {
      return null;
    }
    return redactionPipeline.redact(content);
  }

  /**
   * Compute SHA-256 hash of content.
   *
   * @param content the content to hash
   * @return the Base64-encoded SHA-256 hash
   */
  public String computeHash(String content) {
    if (content == null) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(content.getBytes());
      return Base64.getEncoder().encodeToString(hashBytes);
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute SHA-256 hash", e);
    }
  }

  /**
   * Get the session ID.
   *
   * @return the session ID
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * Get the test ID.
   *
   * @return the test ID
   */
  public String getTestId() {
    return testId;
  }

  /**
   * Get the test being executed.
   *
   * @return the test
   */
  public TafTest getTest() {
    return test;
  }

  /**
   * Get all steps in the test execution.
   *
   * @return list of steps
   */
  public List<TestStep> getSteps() {
    return List.copyOf(steps);
  }

  /**
   * Get all artifacts in the test execution.
   *
   * @return list of artifacts
   */
  public List<TestArtifact> getArtifacts() {
    return List.copyOf(artifacts);
  }

  /**
   * Get the current sequence number.
   *
   * @return the sequence number
   */
  public long getSequenceNumber() {
    return sequenceNumber.get();
  }

  /**
   * Increment the sequence number.
   *
   * @return the new sequence number
   */
  public long nextSequenceNumber() {
    return sequenceNumber.incrementAndGet();
  }

  /**
   * Get the current rerun attempt count.
   *
   * @return the rerun attempt count
   */
  public int getRerunAttempt() {
    return rerunAttempt.get();
  }

  /**
   * Increment the rerun attempt count.
   *
   * @return the new rerun attempt count
   */
  public int nextRerunAttempt() {
    return rerunAttempt.incrementAndGet();
  }

  /**
   * Serialize the collector to a structured result map.
   *
   * @return the serialized result
   */
  public Map<String, Object> toStructuredResult() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sessionId", sessionId);
    result.put("testId", testId);
    result.put("testName", test.name());
    result.put("className", test.className());
    result.put("sequenceNumber", getSequenceNumber());
    result.put("rerunAttempt", getRerunAttempt());

    Map<String, Object> stepsResult = new LinkedHashMap<>();
    for (TestStep step : getSteps()) {
      Map<String, Object> stepResult = new LinkedHashMap<>();
      stepResult.put("name", step.name());
      stepResult.put("status", step.status());
      stepResult.put("description", step.description());
      stepsResult.put(step.name(), stepResult);
    }
    result.put("steps", stepsResult);

    Map<String, Object> artifactsResult = new LinkedHashMap<>();
    for (TestArtifact artifact : getArtifacts()) {
      Map<String, Object> artifactResult = new LinkedHashMap<>();
      artifactResult.put("name", artifact.name());
      artifactResult.put("type", artifact.type());
      artifactResult.put("contentType", artifact.contentType());
      artifactResult.put("content", artifact.content());
      artifactResult.put("hash", artifact.hash());
      artifactResult.put("step", artifact.stepName());
      artifactsResult.put(artifact.name(), artifactResult);
    }
    result.put("artifacts", artifactsResult);

    return result;
  }
}
