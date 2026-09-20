package com.codinglair.taf.mcp.http;

import com.codinglair.taf.mcp.security.Transport;
import com.codinglair.taf.mcp.tools.BlueprintRequestArguments;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldRequest;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldResult;
import com.codinglair.taf.mcp.tools.BlueprintScaffoldingService;
import java.nio.file.Path;
import org.springframework.ai.mcp.annotation.McpTool;

/** Authenticated Streamable HTTP binding for the shared scaffold service. */
public final class HttpScaffoldingTools {
  private final BlueprintScaffoldingService scaffolding;
  private final HttpCallerContext caller;

  HttpScaffoldingTools(BlueprintScaffoldingService scaffolding, HttpCallerContext caller) {
    this.scaffolding = scaffolding;
    this.caller = caller;
  }

  @McpTool(
      name = "scaffold",
      description = "Validate or publish an approved TAF capability blueprint")
  public BlueprintScaffoldResult scaffold(HttpWorkflowRequest request) {
    if (request == null
        || !"1.0".equals(request.schemaVersion())
        || !"scaffold".equals(request.operation()))
      throw new IllegalArgumentException("Valid matching v1 request is required");
    caller.requireScope("taf.tools.scaffold");
    var arguments = request.arguments();
    return scaffolding.execute(
        new BlueprintScaffoldRequest(
            request.requestId(),
            caller.project(),
            caller.environment(),
            Path.of(BlueprintRequestArguments.string(arguments, "destination", true)),
            BlueprintRequestArguments.blueprint(arguments),
            BlueprintRequestArguments.bool(arguments, "publish"),
            request.approvalReference().orElse(null),
            BlueprintRequestArguments.string(arguments, "dependencyApprovalReference", false),
            caller.identity(),
            Transport.STREAMABLE_HTTP));
  }
}
