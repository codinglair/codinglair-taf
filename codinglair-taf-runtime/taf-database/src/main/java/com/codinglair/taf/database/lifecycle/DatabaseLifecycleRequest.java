package com.codinglair.taf.database.lifecycle;

import java.time.Duration;
import java.time.Instant;

public record DatabaseLifecycleRequest(
    String executionId,
    String project,
    String environment,
    String gitRevision,
    String database,
    String migrationVersion,
    String owner,
    String retentionReason,
    DatabaseLifecyclePolicy policy,
    Duration retentionTtl,
    Instant createdAt) {

  public DatabaseLifecycleRequest {
    executionId = required(executionId, "executionId");
    project = required(project, "project");
    environment = required(environment, "environment");
    gitRevision = required(gitRevision, "gitRevision");
    database = required(database, "database");
    migrationVersion = required(migrationVersion, "migrationVersion");
    owner = required(owner, "owner");
    retentionReason = retentionReason == null ? "" : retentionReason.strip();
    policy = policy == null ? DatabaseLifecyclePolicy.EPHEMERAL : policy;
    createdAt = createdAt == null ? Instant.now() : createdAt;
    if (policy == DatabaseLifecyclePolicy.RETAIN_WITH_TTL) {
      if (retentionTtl == null || retentionTtl.isZero() || retentionTtl.isNegative())
        throw new IllegalArgumentException("RETAIN_WITH_TTL requires a positive retentionTtl");
      if (retentionReason.isBlank())
        throw new IllegalArgumentException("RETAIN_WITH_TTL requires a retentionReason");
    }
    if (isProduction(environment) && policy != DatabaseLifecyclePolicy.EXTERNAL)
      throw new IllegalArgumentException("Production database lifecycle must be EXTERNAL");
  }

  public String namespace() {
    return project + "-" + executionId + "-" + database;
  }

  public Instant expiresAt() {
    return policy == DatabaseLifecyclePolicy.RETAIN_WITH_TTL
        ? createdAt.plus(retentionTtl)
        : createdAt;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
      throw new IllegalArgumentException(field + " is invalid");
    return value.strip();
  }

  private static boolean isProduction(String value) {
    return value.equalsIgnoreCase("prod") || value.equalsIgnoreCase("production");
  }
}
