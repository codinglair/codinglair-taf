package com.codinglair.taf.mcp.http;

import com.codinglair.taf.mcp.security.Transport;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import com.codinglair.taf.mcp.tools.ToolOperation;
import com.codinglair.taf.mcp.tools.ToolRequest;
import com.codinglair.taf.mcp.tools.ToolResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.springframework.ai.mcp.annotation.McpTool;

/** Streamable HTTP facade delegating every operation to the shared MCP-006 boundary. */
public final class HttpWorkflowTools {
  private final McpWorkflowTools workflows;
  private final HttpCallerContext caller;
  private final TafMcpHttpProperties limits;

  HttpWorkflowTools(
      McpWorkflowTools workflows, HttpCallerContext caller, TafMcpHttpProperties limits) {
    this.workflows = workflows;
    this.caller = caller;
    this.limits = limits;
  }

  @McpTool(name = "validate", description = "Validate an approved TAF project and environment")
  public ToolResponse validate(HttpWorkflowRequest request) {
    return invoke(ToolOperation.VALIDATE, request);
  }

  @McpTool(name = "compile", description = "Compile an approved TAF test selection asynchronously")
  public ToolResponse compile(HttpWorkflowRequest request) {
    return invoke(ToolOperation.COMPILE, request);
  }

  @McpTool(name = "build", description = "Build an approved TAF test selection asynchronously")
  public ToolResponse build(HttpWorkflowRequest request) {
    return invoke(ToolOperation.BUILD, request);
  }

  @McpTool(name = "execute", description = "Execute an approved TAF test selection asynchronously")
  public ToolResponse execute(HttpWorkflowRequest request) {
    return invoke(ToolOperation.EXECUTE, request);
  }

  @McpTool(name = "cancel", description = "Cancel a scoped TAF job idempotently")
  public ToolResponse cancel(HttpWorkflowRequest request) {
    return invoke(ToolOperation.CANCEL, request);
  }

  private ToolResponse invoke(ToolOperation operation, HttpWorkflowRequest request) {
    if (request == null
        || !"1.0".equals(request.schemaVersion())
        || !operation.action().equals(request.operation())) {
      throw new IllegalArgumentException("Valid matching v1 request is required");
    }
    caller.requireScope("taf.tools." + operation.action());
    return workflows.invoke(
        new ToolRequest(
            request.requestId(),
            operation,
            caller.project(),
            caller.environment(),
            Path.of(argument(request, "workspace", ".")).toAbsolutePath().normalize(),
            optional(argument(request, "selector", null)),
            boundedTimeout(request.timeoutSeconds()),
            request.idempotencyKey().orElse(null),
            request.approvalReference().orElse(null),
            argument(request, "targetJobId", null),
            caller.identity(),
            Transport.STREAMABLE_HTTP));
  }

  private Duration boundedTimeout(long seconds) {
    Duration requested = Duration.ofSeconds(seconds);
    return requested.compareTo(limits.getRequestTimeout()) > 0
        ? limits.getRequestTimeout()
        : requested;
  }

  private static Optional<String> optional(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }

  private static String argument(HttpWorkflowRequest request, String name, String defaultValue) {
    Object value = request.arguments().get(name);
    if (value == null) return defaultValue;
    if (value instanceof String text) return text;
    throw new IllegalArgumentException(name + " must be a string");
  }
}
