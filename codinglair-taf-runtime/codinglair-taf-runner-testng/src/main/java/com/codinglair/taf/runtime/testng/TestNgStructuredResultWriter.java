package com.codinglair.taf.runtime.testng;

import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;

/** Serializes current-execution TestNG attempt history to a bounded vendor-neutral JSON value. */
public final class TestNgStructuredResultWriter {

  private final RedactionPipeline redaction = new RedactionPipeline();

  public String writeJson(TestNgExecutionResult result) {
    StringBuilder json = new StringBuilder(256);
    json.append("{\"testId\":\"")
        .append(value(result.testId()))
        .append("\",\"testName\":\"")
        .append(value(result.testName()))
        .append("\",\"className\":\"")
        .append(value(result.className()))
        .append("\",\"attempts\":[");
    for (int index = 0; index < result.attempts().size(); index++) {
      if (index > 0) {
        json.append(',');
      }
      TestNgAttemptResult attempt = result.attempts().get(index);
      json.append("{\"attemptNumber\":")
          .append(attempt.attemptNumber())
          .append(",\"completedAt\":\"")
          .append(attempt.completedAt())
          .append("\",\"status\":\"")
          .append(attempt.status())
          .append("\",\"failure\":");
      if (attempt.failure() == null) {
        json.append("null");
      } else {
        json.append('"').append(value(attempt.failure().toString())).append('"');
      }
      json.append(",\"artifacts\":[");
      for (int artifactIndex = 0; artifactIndex < attempt.artifacts().size(); artifactIndex++) {
        if (artifactIndex > 0) {
          json.append(',');
        }
        TestArtifact artifact = attempt.artifacts().get(artifactIndex);
        json.append("{\"name\":\"")
            .append(value(artifact.name()))
            .append("\",\"type\":\"")
            .append(value(artifact.type()))
            .append("\",\"contentType\":\"")
            .append(value(artifact.contentType()))
            .append("\",\"content\":\"")
            .append(value(artifact.content()))
            .append("\"}");
      }
      json.append("],\"failureAnalysis\":");
      if (attempt.failureAnalysis() == null) {
        json.append("null");
      } else {
        var analysis = attempt.failureAnalysis();
        json.append("{\"classification\":\"").append(analysis.classification().type())
            .append("\",\"classificationSource\":\"").append(analysis.classification().source())
            .append("\",\"classificationReason\":\"").append(value(analysis.classification().reason()))
            .append("\",\"stability\":\"").append(analysis.stability())
            .append("\",\"historyStatus\":\"").append(analysis.historyStatus())
            .append("\",\"failureSignature\":");
        if (analysis.signature() == null) json.append("null");
        else json.append('"').append(analysis.signature().value()).append('"');
        json.append(",\"signatureAlgorithm\":");
        if (analysis.signature() == null) json.append("null");
        else json.append('"').append(analysis.signature().algorithm()).append('"');
        json.append('}');
      }
      json.append('}');
    }
    return json.append("]}").toString();
  }

  private String value(String input) {
    String safe = redaction.redact(input == null ? "" : input);
    return safe.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }
}
