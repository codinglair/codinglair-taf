package com.codinglair.taf.mcp.stdio;

import com.codinglair.taf.mcp.security.Transport;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import com.codinglair.taf.mcp.tools.RequiredCapability;
import com.codinglair.taf.mcp.tools.ToolOperation;
import com.codinglair.taf.mcp.tools.ToolRequest;
import com.codinglair.taf.mcp.tools.ToolResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.mcp.annotation.McpTool;

/** Spring AI STDIO annotations delegating every operation to the shared MCP-006 service. */
public final class StdioWorkflowTools {
  private final McpWorkflowTools workflows;
  private final TafMcpStdioProperties properties;

  public StdioWorkflowTools(McpWorkflowTools workflows, TafMcpStdioProperties properties) {
    this.workflows = workflows;
    this.properties = properties;
  }

  @McpTool(name = "validate", description = "Validate an approved TAF project and environment")
  public ToolResponse validate(StdioWorkflowRequest request) {
    return invoke(ToolOperation.VALIDATE, request);
  }

  @McpTool(name = "compile", description = "Compile an approved TAF test selection asynchronously")
  public ToolResponse compile(StdioWorkflowRequest request) {
    return invoke(ToolOperation.COMPILE, request);
  }

  @McpTool(name = "build", description = "Build an approved TAF test selection asynchronously")
  public ToolResponse build(StdioWorkflowRequest request) {
    return invoke(ToolOperation.BUILD, request);
  }

  @McpTool(name = "execute", description = "Execute an approved TAF test selection asynchronously")
  public ToolResponse execute(StdioWorkflowRequest request) {
    return invoke(ToolOperation.EXECUTE, request);
  }

  @McpTool(name = "cancel", description = "Cancel a scoped TAF job idempotently")
  public ToolResponse cancel(StdioWorkflowRequest request) {
    return invoke(ToolOperation.CANCEL, request);
  }

  private ToolResponse invoke(ToolOperation operation, StdioWorkflowRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is required");
    }
    if (!"1.0".equals(request.schemaVersion()) || !operation.action().equals(request.operation())) {
      throw new IllegalArgumentException(
          "schemaVersion or operation does not match the invoked tool");
    }
    String project = defaulted(request.projectId(), properties.getProject());
    String environment = defaulted(request.environment(), properties.getEnvironment());
    Path workspace = resolveWorkspace(argument(request, "workspace"));
    return workflows.invoke(
        new ToolRequest(
            request.requestId(),
            operation,
            project,
            environment,
            workspace,
            optional(argument(request, "selector")),
            Duration.ofSeconds(request.timeoutSeconds()),
            request.idempotencyKey().orElse(null),
            request.approvalReference().orElse(null),
            argument(request, "targetJobId"),
            properties.identity(),
            Transport.STDIO,
            requiredCapabilities(request.arguments())));
  }

  private Path resolveWorkspace(String workspace) {
    Path root = properties.getWorkspaceRoot().toAbsolutePath().normalize();
    return workspace == null || workspace.isBlank() ? root : root.resolve(workspace).normalize();
  }

  private static String defaulted(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private static Optional<String> optional(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }

  private static String argument(StdioWorkflowRequest request, String name) {
    Object value = request.arguments().get(name);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String text)) {
      throw new IllegalArgumentException(name + " must be a string");
    }
    return text;
  }

  private static List<RequiredCapability> requiredCapabilities(Map<String, Object> arguments) {
    Object value = arguments.get("requiredCapabilities");
    if (value == null) return List.of();
    if (!(value instanceof List<?> entries))
      throw new IllegalArgumentException("requiredCapabilities must be an array");
    return entries.stream().map(StdioWorkflowTools::requiredCapability).toList();
  }

  private static RequiredCapability requiredCapability(Object value) {
    if (!(value instanceof Map<?, ?> entry)
        || !(entry.get("capabilityId") instanceof String capability)
        || !(entry.get("instance") instanceof String instance)
        || !(entry.get("operations") instanceof List<?> operations)
        || operations.stream().anyMatch(item -> !(item instanceof String))) {
      throw new IllegalArgumentException("required capability is invalid");
    }
    return new RequiredCapability(
        capability,
        instance,
        operations.stream().map(String.class::cast).collect(java.util.stream.Collectors.toSet()));
  }
}
