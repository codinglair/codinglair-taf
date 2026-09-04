package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.Transport;
import java.nio.file.Path;

public record ScaffoldRequest(
    String requestId,
    String projectId,
    String environment,
    Path workspace,
    Path destination,
    String template,
    String templateVersion,
    String writeApprovalId,
    String dependencyApprovalId,
    CallerIdentity identity,
    Transport transport) {
  public ScaffoldRequest {
    if (!token(requestId, 128)
        || !token(projectId, 128)
        || !token(environment, 64)
        || workspace == null
        || destination == null
        || !token(template, 128)
        || !token(templateVersion, 128)
        || identity == null
        || transport == null) {
      throw new IllegalArgumentException("valid bounded scaffold request fields are required");
    }
  }

  private static boolean token(String value, int maximum) {
    return value != null && value.matches("[A-Za-z0-9._-]{1," + maximum + "}");
  }
}
