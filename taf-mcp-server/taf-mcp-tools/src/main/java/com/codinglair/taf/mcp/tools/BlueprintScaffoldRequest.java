package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.Transport;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Transport-neutral request for validating or publishing an approved capability blueprint. */
public record BlueprintScaffoldRequest(
    String requestId,
    String projectId,
    String environment,
    Path destination,
    BlueprintCompositionEngine.Request blueprint,
    boolean publish,
    String writeApprovalId,
    String dependencyApprovalId,
    CallerIdentity identity,
    Transport transport) {
  public BlueprintScaffoldRequest {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(environment, "environment");
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(blueprint, "blueprint");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(transport, "transport");
  }

  public static BlueprintCompositionEngine.Request blueprint(
      String groupId,
      String artifactId,
      String basePackage,
      String tafVersion,
      List<String> capabilities,
      String messagingProvider,
      String mobilePlatform,
      String mobileAutomationName,
      String runner,
      String reporting,
      String testDefinitions) {
    return new BlueprintCompositionEngine.Request(
        groupId,
        artifactId,
        basePackage,
        tafVersion,
        capabilities,
        messagingProvider,
        mobilePlatform,
        mobileAutomationName,
        runner,
        reporting,
        testDefinitions);
  }
}
