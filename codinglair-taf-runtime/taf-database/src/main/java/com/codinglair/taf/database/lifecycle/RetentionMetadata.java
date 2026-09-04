package com.codinglair.taf.database.lifecycle;

import java.time.Instant;

public record RetentionMetadata(
    String executionId,
    String project,
    String environment,
    String gitRevision,
    String database,
    String migrationVersion,
    Instant createdAt,
    Instant expiresAt,
    String owner,
    String retentionReason,
    String namespace,
    CleanupStatus cleanupStatus,
    String sanitizedOutcome) {

  public RetentionMetadata withCleanup(CleanupStatus status, String outcome) {
    return new RetentionMetadata(
        executionId,
        project,
        environment,
        gitRevision,
        database,
        migrationVersion,
        createdAt,
        expiresAt,
        owner,
        retentionReason,
        namespace,
        status,
        outcome);
  }

  static RetentionMetadata retained(DatabaseLifecycleRequest request) {
    return new RetentionMetadata(
        request.executionId(),
        request.project(),
        request.environment(),
        request.gitRevision(),
        request.database(),
        request.migrationVersion(),
        request.createdAt(),
        request.expiresAt(),
        request.owner(),
        request.retentionReason(),
        request.namespace(),
        CleanupStatus.RETAINED,
        "retained until expiry");
  }
}
