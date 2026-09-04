package com.codinglair.taf.runtime.core.reporting;

import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.util.List;

/** StructuredResultWriter handles writing test results in various formats. */
public class StructuredResultWriter {

  private final RedactionPipeline redactionPipeline;

  public StructuredResultWriter() {
    this.redactionPipeline = new RedactionPipeline();
  }

  /**
   * Write results in JSON format.
   *
   * @param sessionId the session ID
   * @param testId the test ID
   * @param testName the test name
   * @param steps the steps
   * @param artifacts the artifacts
   * @return the JSON string
   */
  public String writeJson(
      String sessionId,
      String testId,
      String testName,
      List<TestStep> steps,
      List<TestArtifact> artifacts) {
    return writeJson(sessionId, testId, testName, steps, artifacts, null);
  }

  public String writeJson(String sessionId, String testId, String testName, List<TestStep> steps,
      List<TestArtifact> artifacts, FailureAnalysis analysis) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"sessionId\": \"").append(jsonValue(sessionId)).append("\",\n");
    json.append("  \"testId\": \"").append(jsonValue(testId)).append("\",\n");
    json.append("  \"testName\": \"").append(jsonValue(testName)).append("\",\n");
    json.append("  \"steps\": [\n");

    for (int i = 0; i < steps.size(); i++) {
      TestStep step = steps.get(i);
      json.append("    {\n");
      json.append("      \"name\": \"").append(jsonValue(step.name())).append("\",\n");
      json.append("      \"status\": \"").append(jsonValue(step.status())).append("\"");
      if (step.description() != null) {
        json.append(",\n      \"description\": \"").append(jsonValue(step.description())).append("\"");
      }
      json.append('\n');
      json.append("    }");
      if (i < steps.size() - 1) {
        json.append(",");
      }
      json.append("\n");
    }

    json.append("  ],\n");
    json.append("  \"artifacts\": [\n");

    for (int i = 0; i < artifacts.size(); i++) {
      TestArtifact artifact = artifacts.get(i);
      json.append("    {\n");
      json.append("      \"name\": \"").append(jsonValue(artifact.name())).append("\",\n");
      json.append("      \"type\": \"").append(jsonValue(artifact.type())).append("\",\n");
      json.append("      \"contentType\": \"")
          .append(jsonValue(artifact.contentType()))
          .append("\",\n");
      json.append("      \"content\": \"").append(jsonValue(artifact.content())).append("\"");
      json.append("\n    }");
      if (i < artifacts.size() - 1) {
        json.append(",");
      }
      json.append("\n");
    }

    json.append("  ],\n");
    json.append("  \"failureAnalysis\": ").append(failureJson(analysis)).append("\n");
    json.append("}");

    return json.toString();
  }

  /**
   * Write results in JUnit XML format.
   *
   * @param sessionId the session ID
   * @param testId the test ID
   * @param testName the test name
   * @param className the class name
   * @param steps the steps
   * @return the XML string
   */
  public String writeJUnitXml(
      String sessionId, String testId, String testName, String className, List<TestStep> steps) {
    return writeJUnitXml(sessionId, testId, testName, className, steps, null);
  }

  public String writeJUnitXml(String sessionId, String testId, String testName, String className,
      List<TestStep> steps, FailureAnalysis analysis) {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<testsuite tests=\"1\" name=\"")
        .append(escapeXml(testName))
        .append("\" hostname=\"test-machine\">\n");
    xml.append("  <properties>\n");
    property(xml, "taf.session.id", sessionId);
    property(xml, "taf.test.id", testId);
    if (analysis != null) {
      property(xml, "taf.failure.classification", analysis.classification().type().name());
      property(xml, "taf.failure.source", analysis.classification().source().name());
      property(xml, "taf.failure.reason", analysis.classification().reason());
      property(xml, "taf.failure.stability", analysis.stability().name());
      property(xml, "taf.failure.history", analysis.historyStatus().name());
      if (analysis.signature() != null) {
        property(xml, "taf.failure.signature", analysis.signature().value());
        property(xml, "taf.failure.signature.algorithm", analysis.signature().algorithm());
      }
    }
    xml.append("  </properties>\n");
    xml.append("  <testcase classname=\"")
        .append(escapeXml(className))
        .append("\" name=\"")
        .append(escapeXml(testName))
        .append("\" time=\"0.001\">\n");

    if (!steps.isEmpty()) {
      xml.append("    <system-out>");
      for (TestStep step : steps) {
        xml.append(escapeXml("step:" + step.name() + "=" + step.status() + "\n"));
      }
      xml.append("</system-out>\n");
    }

    xml.append("  </testcase>\n");
    xml.append("</testsuite>");

    return xml.toString();
  }

  /**
   * Escape special characters in JSON strings.
   *
   * @param value the value to escape
   * @return the escaped value
   */
  private String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (char c : value.toCharArray()) {
      switch (c) {
        case '\\':
          sb.append("\\\\");
          break;
        case '"':
          sb.append("\\\"");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          sb.append(c);
          break;
      }
    }
    return sb.toString();
  }

  /**
   * Escape special characters in XML strings.
   *
   * @param value the value to escape
   * @return the escaped value
   */
  private String escapeXml(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (char c : value.toCharArray()) {
      switch (c) {
        case '&':
          sb.append("&amp;");
          break;
        case '<':
          sb.append("&lt;");
          break;
        case '>':
          sb.append("&gt;");
          break;
        case '"':
          sb.append("&quot;");
          break;
        case '\'':
          sb.append("&apos;");
          break;
        default:
          sb.append(c);
          break;
      }
    }
    return sb.toString();
  }

  private String failureJson(FailureAnalysis analysis) {
    if (analysis == null) return "null";
    String signature = analysis.signature() == null ? "null" : "\"" + escapeJson(analysis.signature().value()) + "\"";
    String algorithm = analysis.signature() == null ? "null" : "\"" + escapeJson(analysis.signature().algorithm()) + "\"";
    return "{\"classification\":\"" + analysis.classification().type()
        + "\",\"classificationSource\":\"" + analysis.classification().source()
        + "\",\"classificationReason\":\"" + escapeJson(redact(analysis.classification().reason()))
        + "\",\"stability\":\"" + analysis.stability() + "\",\"historyStatus\":\""
        + analysis.historyStatus() + "\",\"failureSignature\":" + signature
        + ",\"signatureAlgorithm\":" + algorithm + "}";
  }

  private void property(StringBuilder xml, String name, String value) {
    xml.append("      <property name=\"").append(escapeXml(name)).append("\" value=\"")
        .append(escapeXml(redact(value))).append("\"/>\n");
  }

  /**
   * Redact sensitive information from the given content.
   *
   * @param content the content to redact
   * @return the redacted content
   */
  private String redact(String content) {
    if (content == null) {
      return null;
    }
    return redactionPipeline.redact(content);
  }

  private String jsonValue(String content) {
    return escapeJson(redact(content));
  }
}
