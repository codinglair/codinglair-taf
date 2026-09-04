package com.codinglair.taf.mcp.stdio;

import com.codinglair.taf.mcp.prompts.DiagnosticKind;
import com.codinglair.taf.mcp.prompts.DiagnosticResult;
import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.prompts.PromptDefinition;
import com.codinglair.taf.mcp.resources.ResourceRequestContext;
import com.codinglair.taf.mcp.security.Transport;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;

/** STDIO facade for the governed MCP-010 prompt, report, evidence, and diagnostic service. */
public final class StdioPromptReports {
  private final McpPromptReportService service;
  private final TafMcpStdioProperties properties;

  StdioPromptReports(McpPromptReportService service, TafMcpStdioProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  @McpPrompt(name = "taf.qa.failure-analysis", description = "Analyze sanitized failure evidence")
  public String failureAnalysis(Map<String, String> arguments) {
    return render("taf.qa.failure-analysis", arguments);
  }

  @McpPrompt(
      name = "taf.qa.execution-summary",
      description = "Summarize a completed test execution")
  public String executionSummary(Map<String, String> arguments) {
    return render("taf.qa.execution-summary", arguments);
  }

  @McpPrompt(
      name = "taf.qa.environment-triage",
      description = "Triage a sanitized environment failure")
  public String environmentTriage(Map<String, String> arguments) {
    return render("taf.qa.environment-triage", arguments);
  }

  @McpResource(
      name = "taf-report",
      uri = "taf://report/{jobId}",
      description = "Authorized bounded TAF execution report",
      mimeType = "text/plain")
  public String report(String jobId) {
    return service.report(context(), correlationId(), Transport.STDIO, jobId, null).toString();
  }

  @McpResource(
      name = "taf-evidence",
      uri = "taf://evidence/{jobId}/{evidenceId}",
      description = "Authorized bounded TAF execution evidence",
      mimeType = "text/plain")
  public String evidence(String jobId, String evidenceId) {
    return service
        .evidence(context(), correlationId(), Transport.STDIO, jobId, evidenceId, null)
        .toString();
  }

  @McpTool(name = "diagnose", description = "Return a bounded server-owned diagnostic summary")
  public DiagnosticResult diagnose(String jobId, DiagnosticKind kind) {
    return service.diagnose(context(), correlationId(), Transport.STDIO, jobId, kind);
  }

  private String render(String name, Map<String, String> arguments) {
    PromptDefinition prompt = service.prompt(context(), correlationId(), Transport.STDIO, name);
    String reference = arguments == null ? null : arguments.get("reportReference");
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reportReference is required");
    }
    return prompt.messages().getFirst().text().replace("{reportReference}", reference);
  }

  private ResourceRequestContext context() {
    return new ResourceRequestContext(
        properties.identity(), properties.getProject(), properties.getEnvironment());
  }

  private static String correlationId() {
    return UUID.randomUUID().toString();
  }
}
