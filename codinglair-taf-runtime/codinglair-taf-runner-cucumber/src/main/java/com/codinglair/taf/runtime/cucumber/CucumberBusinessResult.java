package com.codinglair.taf.runtime.cucumber;

import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import java.net.URI;
import java.util.List;

/** Business-facing scenario result with traceability tags and sanitized evidence. */
public record CucumberBusinessResult(
    String scenarioId,
    String scenarioName,
    URI featureUri,
    int line,
    String status,
    List<String> traceabilityTags,
    List<BusinessStepResult> steps,
    List<TestArtifact> artifacts,
    FailureAnalysis failureAnalysis) {

  public CucumberBusinessResult {
    traceabilityTags = List.copyOf(traceabilityTags);
    steps = List.copyOf(steps);
    artifacts = List.copyOf(artifacts);
  }

  public CucumberBusinessResult(String scenarioId, String scenarioName, URI featureUri, int line,
      String status, List<String> traceabilityTags, List<BusinessStepResult> steps,
      List<TestArtifact> artifacts) {
    this(scenarioId, scenarioName, featureUri, line, status, traceabilityTags, steps, artifacts, null);
  }

  /** Explicit audience marker prevents accidental mixing with technical results. */
  public String audience() {
    return "BUSINESS";
  }
}
