package com.codinglair.taf.database.lifecycle;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates one independently governed lifecycle per execution and logical database. */
public final class DatabaseLifecycleCoordinator {
  private final DatabaseLifecycleOperations operations;
  private final DatabaseSnapshotSanitizer sanitizer;
  private final LifecycleEvidenceSink evidence;
  private final Clock clock;
  private final Map<Key, State> active = new ConcurrentHashMap<>();
  private final Map<Key, RetentionMetadata> retained = new ConcurrentHashMap<>();

  public DatabaseLifecycleCoordinator(
      DatabaseLifecycleOperations operations,
      DatabaseSnapshotSanitizer sanitizer,
      LifecycleEvidenceSink evidence,
      Clock clock) {
    this.operations = Objects.requireNonNull(operations, "operations");
    this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    this.evidence = Objects.requireNonNull(evidence, "evidence");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public void prepare(DatabaseLifecycleRequest request) {
    Objects.requireNonNull(request, "request");
    Key key = Key.of(request);
    State state = new State();
    if (active.putIfAbsent(key, state) != null)
      throw failure(request, "Database lifecycle is already active", null);
    try {
      if (request.policy() == DatabaseLifecyclePolicy.EXTERNAL) return;
      if (request.policy() != DatabaseLifecyclePolicy.RESET) operations.provision(request);
      operations.verifyReady(request);
      if (request.policy() == DatabaseLifecyclePolicy.RESET) operations.reset(request);
      operations.migrate(request);
      operations.seed(request);
    } catch (RuntimeException cause) {
      active.remove(key, state);
      throw failure(request, "Database lifecycle preparation failed", cause);
    }
  }

  public void complete(
      DatabaseLifecycleRequest request, ExecutionOutcome outcome, Throwable originalFailure) {
    Objects.requireNonNull(outcome, "outcome");
    Key key = Key.of(request);
    State state = active.get(key);
    if (state == null || !state.completed.compareAndSet(false, true)) return;
    DatabaseLifecycleException lifecycleFailure = null;
    try {
      if (request.policy() == DatabaseLifecyclePolicy.EXTERNAL) return;
      if (request.policy() == DatabaseLifecyclePolicy.RESET) return;
      if (request.policy() == DatabaseLifecyclePolicy.SNAPSHOT_ON_FAILURE
          && outcome != ExecutionOutcome.PASSED) {
        String sanitized = sanitizer.sanitize(operations.exportSnapshot(request));
        evidence.snapshot(request.executionId(), request.database(), sanitized);
      }
      if (request.policy() == DatabaseLifecyclePolicy.RETAIN_WITH_TTL) {
        RetentionMetadata metadata = RetentionMetadata.retained(request);
        retained.put(key, metadata);
        evidence.audit(metadata);
        return;
      }
      cleanup(request, key);
    } catch (RuntimeException cause) {
      RetentionMetadata failed = metadata(request, CleanupStatus.FAILED, "cleanup failed");
      evidence.audit(failed);
      lifecycleFailure = failure(request, "Database lifecycle completion failed", cause);
    } finally {
      active.remove(key, state);
    }
    if (lifecycleFailure != null) {
      if (originalFailure != null) originalFailure.addSuppressed(lifecycleFailure);
      else throw lifecycleFailure;
    }
  }

  public int expireRetained() {
    Instant now = clock.instant();
    int cleaned = 0;
    for (Map.Entry<Key, RetentionMetadata> entry : retained.entrySet()) {
      if (entry.getValue().expiresAt().isAfter(now)) continue;
      Key key = entry.getKey();
      RetentionMetadata metadata = entry.getValue();
      try {
        operations.cleanup(toRequest(metadata));
        RetentionMetadata complete =
            metadata.withCleanup(CleanupStatus.COMPLETED, "expired data removed");
        evidence.audit(complete);
        if (retained.remove(key, metadata)) cleaned++;
      } catch (RuntimeException cause) {
        evidence.audit(metadata.withCleanup(CleanupStatus.FAILED, "expiry cleanup failed"));
      }
    }
    return cleaned;
  }

  private void cleanup(DatabaseLifecycleRequest request, Key key) {
    operations.cleanup(request);
    evidence.audit(metadata(request, CleanupStatus.COMPLETED, "database removed"));
    retained.remove(key);
  }

  private static RetentionMetadata metadata(
      DatabaseLifecycleRequest r, CleanupStatus status, String outcome) {
    return new RetentionMetadata(
        r.executionId(),
        r.project(),
        r.environment(),
        r.gitRevision(),
        r.database(),
        r.migrationVersion(),
        r.createdAt(),
        r.expiresAt(),
        r.owner(),
        r.retentionReason(),
        r.namespace(),
        status,
        outcome);
  }

  private static DatabaseLifecycleRequest toRequest(RetentionMetadata m) {
    return new DatabaseLifecycleRequest(
        m.executionId(),
        m.project(),
        m.environment(),
        m.gitRevision(),
        m.database(),
        m.migrationVersion(),
        m.owner(),
        m.retentionReason(),
        DatabaseLifecyclePolicy.RETAIN_WITH_TTL,
        java.time.Duration.between(m.createdAt(), m.expiresAt()),
        m.createdAt());
  }

  private static DatabaseLifecycleException failure(
      DatabaseLifecycleRequest request, String message, Throwable cause) {
    return new DatabaseLifecycleException(
        message,
        request.executionId(),
        request.database(),
        "inspect sanitized lifecycle audit evidence and retry the execution-owned operation",
        cause);
  }

  private record Key(String executionId, String database) {
    static Key of(DatabaseLifecycleRequest request) {
      return new Key(request.executionId(), request.database());
    }
  }

  private static final class State {
    private final AtomicBoolean completed = new AtomicBoolean();
  }
}
