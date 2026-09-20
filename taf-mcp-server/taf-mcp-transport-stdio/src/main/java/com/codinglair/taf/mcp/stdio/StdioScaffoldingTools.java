package com.codinglair.taf.mcp.stdio;

import com.codinglair.taf.mcp.security.Transport;
import com.codinglair.taf.mcp.tools.BlueprintRequestArguments;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldRequest;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldResult;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldingService;
import java.nio.file.Path;
import org.springframework.ai.mcp.annotation.McpTool;

/** STDIO binding for capability-driven validation and approved scaffold publication. */
public final class StdioScaffoldingTools {
  private final BlueprintScaffoldingService scaffolding;
  private final TafMcpStdioProperties properties;

  public StdioScaffoldingTools(
      BlueprintScaffoldingService scaffolding, TafMcpStdioProperties properties) {
    this.scaffolding = scaffolding;
    this.properties = properties;
  }

  @McpTool(
      name = "scaffold",
      description = "Validate or publish an approved TAF capability blueprint")
  public BlueprintScaffoldResult scaffold(StdioWorkflowRequest request) {
    if (request == null
        || !"1.0".equals(request.schemaVersion())
        || !"scaffold".equals(request.operation()))
      throw new IllegalArgumentException("Valid matching v1 request is required");
    var arguments = request.arguments();
    String project = defaulted(request.projectId(), properties.getProject());
    String environment = defaulted(request.environment(), properties.getEnvironment());
    Path destination = Path.of(BlueprintRequestArguments.string(arguments, "destination", true));
    return scaffolding.execute(
        new BlueprintScaffoldRequest(
            request.requestId(),
            project,
            environment,
            destination,
            BlueprintRequestArguments.blueprint(arguments),
            BlueprintRequestArguments.bool(arguments, "publish"),
            request.approvalReference().orElse(null),
            BlueprintRequestArguments.string(arguments, "dependencyApprovalReference", false),
            properties.identity(),
            Transport.STDIO));
  }

  private static String defaulted(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
