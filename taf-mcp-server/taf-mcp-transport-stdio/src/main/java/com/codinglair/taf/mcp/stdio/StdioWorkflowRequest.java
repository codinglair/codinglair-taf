package com.codinglair.taf.mcp.stdio;

import java.util.Map;
import java.util.Optional;

/** Spring AI representation of the transport-neutral v1 tool request envelope. */
public record StdioWorkflowRequest(
    String schemaVersion,
    String requestId,
    String operation,
    String projectId,
    String environment,
    long timeoutSeconds,
    Optional<String> idempotencyKey,
    Optional<String> approvalReference,
    Map<String, Object> arguments) {
  public StdioWorkflowRequest {
    idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
    approvalReference = approvalReference == null ? Optional.empty() : approvalReference;
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
  }
}
