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
      if (index > 0) json.append(',');
      appendAttempt(json, result.attempts().get(index));
    }
    return json.append("]}").toString();
  }

  private void appendAttempt(StringBuilder json, TestNgAttemptResult attempt) {
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
    appendArtifacts(json, attempt);
    json.append("],\"failureAnalysis\":");
    appendFailureAnalysis(json, attempt);
    json.append('}');
  }

  private void appendArtifacts(StringBuilder json, TestNgAttemptResult attempt) {
    for (int index = 0; index < attempt.artifacts().size(); index++) {
      if (index > 0) json.append(',');
      TestArtifact artifact = attempt.artifacts().get(index);
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
  }

  private void appendFailureAnalysis(StringBuilder json, TestNgAttemptResult attempt) {
    if (attempt.failureAnalysis() == null) {
      json.append("null");
      return;
    }
    var analysis = attempt.failureAnalysis();
    json.append("{\"classification\":\"")
        .append(analysis.classification().type())
        .append("\",\"classificationSource\":\"")
        .append(analysis.classification().source())
        .append("\",\"classificationReason\":\"")
        .append(value(analysis.classification().reason()))
        .append("\",\"stability\":\"")
        .append(analysis.stability())
        .append("\",\"historyStatus\":\"")
        .append(analysis.historyStatus())
        .append("\",\"failureSignature\":");
    if (analysis.signature() == null) json.append("null");
    else json.append('"').append(analysis.signature().value()).append('"');
    json.append(",\"signatureAlgorithm\":");
    if (analysis.signature() == null) json.append("null");
    else json.append('"').append(analysis.signature().algorithm()).append('"');
    json.append('}');
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
