package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.security.CallerIdentity;
import com.codinglair.taf.mcp.security.Transport;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record ToolRequest(
    String requestId,
    ToolOperation operation,
    String projectId,
    String environment,
    Path workspace,
    Optional<String> selector,
    Duration timeout,
    String idempotencyKey,
    String approvalId,
    String targetJobId,
    CallerIdentity identity,
    Transport transport) {
  public ToolRequest {
    requestId = text(requestId, "requestId", 128);
    Objects.requireNonNull(operation, "operation");
    projectId = token(projectId, "projectId", 128);
    environment = token(environment, "environment", 64);
    Objects.requireNonNull(workspace, "workspace");
    selector = Objects.requireNonNull(selector, "selector").map(v -> text(v, "selector", 256));
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException("timeout must be positive");
    if (operation.asynchronous()) text(idempotencyKey, "idempotencyKey", 128);
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(transport, "transport");
  }

  private static String token(String value, String name, int maximum) {
    value = text(value, name, maximum);
    if (!value.matches("[A-Za-z0-9._-]+"))
      throw new IllegalArgumentException(name + " contains invalid characters");
    return value;
  }

  private static String text(String value, String name, int maximum) {
    if (value == null
        || value.isBlank()
        || value.length() > maximum
        || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException(name + " must be nonblank, bounded, and free of controls");
    return value;
  }
}
