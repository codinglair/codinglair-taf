package com.codinglair.taf.mcp.http;

import com.codinglair.taf.mcp.prompts.DiagnosticKind;
import com.codinglair.taf.mcp.prompts.DiagnosticResult;
import com.codinglair.taf.mcp.prompts.McpPromptReportService;
import com.codinglair.taf.mcp.prompts.PromptDefinition;
import com.codinglair.taf.mcp.resources.ResourceRequestContext;
import com.codinglair.taf.mcp.security.Transport;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

/** Streamable HTTP facade for the governed MCP-010 prompt and report service. */
public final class HttpPromptReports {
  private final McpPromptReportService service;
  private final HttpCallerContext caller;

  HttpPromptReports(McpPromptReportService service, HttpCallerContext caller) {
    this.service = service;
    this.caller = caller;
  }

  @McpPrompt(name = "taf.qa.failure-analysis", description = "Analyze sanitized failure evidence")
  public String failureAnalysis(
      McpSyncRequestContext requestContext,
      @McpArg(
              name = "reportReference",
              description = "Controlled report reference",
              required = true)
          String reportReference) {
    return render(requestContext, "taf.qa.failure-analysis", reportReference);
  }

  @McpPrompt(
      name = "taf.qa.execution-summary",
      description = "Summarize a completed test execution")
  public String executionSummary(
      McpSyncRequestContext requestContext,
      @McpArg(
              name = "reportReference",
              description = "Controlled report reference",
              required = true)
          String reportReference) {
    return render(requestContext, "taf.qa.execution-summary", reportReference);
  }

  @McpPrompt(
      name = "taf.qa.environment-triage",
      description = "Triage a sanitized environment failure")
  public String environmentTriage(
      McpSyncRequestContext requestContext,
      @McpArg(
              name = "reportReference",
              description = "Controlled report reference",
              required = true)
          String reportReference) {
    return render(requestContext, "taf.qa.environment-triage", reportReference);
  }

  /** Retains the original Java adapter entry point while MCP uses explicit annotated arguments. */
  public String failureAnalysis(Map<String, String> arguments) {
    return render("taf.qa.failure-analysis", reference(arguments));
  }

  /** Retains the original Java adapter entry point while MCP uses explicit annotated arguments. */
  public String executionSummary(Map<String, String> arguments) {
    return render("taf.qa.execution-summary", reference(arguments));
  }

  /** Retains the original Java adapter entry point while MCP uses explicit annotated arguments. */
  public String environmentTriage(Map<String, String> arguments) {
    return render("taf.qa.environment-triage", reference(arguments));
  }

  @McpResource(
      name = "taf-report",
      uri = "taf://report/{jobId}",
      description = "Authorized bounded TAF execution report",
      mimeType = "text/plain")
  public String report(String jobId) {
    caller.requireScope("taf.resources.read");
    return service
        .report(context(), correlationId(), Transport.STREAMABLE_HTTP, jobId, null)
        .toString();
  }

  @McpResource(
      name = "taf-evidence",
      uri = "taf://evidence/{jobId}/{evidenceId}",
      description = "Authorized bounded TAF execution evidence",
      mimeType = "text/plain")
  public String evidence(String jobId, String evidenceId) {
    caller.requireScope("taf.resources.read");
    return service
        .evidence(context(), correlationId(), Transport.STREAMABLE_HTTP, jobId, evidenceId, null)
        .toString();
  }

  @McpTool(name = "diagnose", description = "Return a bounded server-owned diagnostic summary")
  public DiagnosticResult diagnose(String jobId, DiagnosticKind kind) {
    caller.requireScope("taf.tools.diagnose");
    return service.diagnose(context(), correlationId(), Transport.STREAMABLE_HTTP, jobId, kind);
  }

  private String render(String name, String reference) {
    caller.requireScope("taf.prompts.read");
    PromptDefinition prompt =
        service.prompt(context(), correlationId(), Transport.STREAMABLE_HTTP, name);
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reportReference is required");
    }
    return prompt.messages().getFirst().text().replace("{reportReference}", reference);
  }

  private String render(McpSyncRequestContext requestContext, String name, String reference) {
    var transportContext = requestContext.transportContext();
    caller.requireScope(transportContext, "taf.prompts.read");
    PromptDefinition prompt =
        service.prompt(context(transportContext), correlationId(), Transport.STREAMABLE_HTTP, name);
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reportReference is required");
    }
    return prompt.messages().getFirst().text().replace("{reportReference}", reference);
  }

  private static String reference(Map<String, String> arguments) {
    return arguments == null ? null : arguments.get("reportReference");
  }

  private ResourceRequestContext context() {
    return new ResourceRequestContext(caller.identity(), caller.project(), caller.environment());
  }

  private ResourceRequestContext context(
      io.modelcontextprotocol.common.McpTransportContext context) {
    return new ResourceRequestContext(
        caller.identity(context), caller.project(context), caller.environment(context));
  }

  private static String correlationId() {
    return UUID.randomUUID().toString();
  }
}
