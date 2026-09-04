package com.codinglair.taf.mcp.http;

import java.util.Map;
import java.util.Optional;

/** HTTP representation of the same v1 request envelope used by STDIO. */
public record HttpWorkflowRequest(
    String schemaVersion,
    String requestId,
    String operation,
    long timeoutSeconds,
    Optional<String> idempotencyKey,
    Optional<String> approvalReference,
    Map<String, Object> arguments) {
  public HttpWorkflowRequest {
    idempotencyKey = idempotencyKey == null ? Optional.empty() : idempotencyKey;
    approvalReference = approvalReference == null ? Optional.empty() : approvalReference;
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
  }
}
