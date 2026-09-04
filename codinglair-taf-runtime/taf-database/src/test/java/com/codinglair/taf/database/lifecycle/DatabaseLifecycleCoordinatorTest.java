package com.codinglair.taf.database.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Governed database lifecycle coordinator")
class DatabaseLifecycleCoordinatorTest {
  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

  @Nested
  @DisplayName("Policy contracts")
  class PolicyContracts {
    @ParameterizedTest
    @EnumSource(value = ExecutionOutcome.class)
    @DisplayName("Ephemeral databases clean up for every terminal outcome")
    void ephemeralCleansForEveryOutcome(ExecutionOutcome outcome) {
      Fixture fixture = new Fixture();
      DatabaseLifecycleRequest request = request("run-1", DatabaseLifecyclePolicy.EPHEMERAL);

      fixture.coordinator.prepare(request);
      fixture.coordinator.complete(request, outcome, null);

      assertThat(fixture.operations.calls)
          .containsExactly(
              "provision:run-1", "ready:run-1", "migrate:run-1", "seed:run-1", "cleanup:run-1");
    }

    @Test
    @DisplayName("Reset restores baseline before migration and seeding")
    void resetOrdering() {
      Fixture fixture = new Fixture();
      DatabaseLifecycleRequest request = request("run-1", DatabaseLifecyclePolicy.RESET);
      fixture.coordinator.prepare(request);
      fixture.coordinator.complete(request, ExecutionOutcome.PASSED, null);
      assertThat(fixture.operations.calls)
          .containsExactly("ready:run-1", "reset:run-1", "migrate:run-1", "seed:run-1");
    }

    @Test
    @DisplayName("Failure snapshot is sanitized, exported once, and followed by cleanup")
    void snapshotIsSanitizedOnce() {
      Fixture fixture = new Fixture();
      DatabaseLifecycleRequest request =
          request("run-1", DatabaseLifecyclePolicy.SNAPSHOT_ON_FAILURE);
      fixture.coordinator.prepare(request);
      fixture.coordinator.complete(request, ExecutionOutcome.FAILED, new AssertionError("test"));
      fixture.coordinator.complete(request, ExecutionOutcome.FAILED, null);
      assertThat(fixture.evidence.snapshots).containsExactly("run-1:[REDACTED]");
      assertThat(fixture.operations.calls).contains("snapshot:run-1", "cleanup:run-1");
    }

    @Test
    @DisplayName("External policy performs no lifecycle operation")
    void externalDoesNothing() {
      Fixture fixture = new Fixture();
      DatabaseLifecycleRequest request = request("run-1", DatabaseLifecyclePolicy.EXTERNAL);
      fixture.coordinator.prepare(request);
      fixture.coordinator.complete(request, ExecutionOutcome.FAILED, null);
      assertThat(fixture.operations.calls).isEmpty();
    }

    @Test
    @DisplayName("Production rejects every destructive policy")
    void productionRejectsDestructivePolicy() {
      assertThatThrownBy(
              () ->
                  new DatabaseLifecycleRequest(
                      "run",
                      "project",
                      "production",
                      "revision",
                      "orders",
                      "1",
                      "owner",
                      "",
                      DatabaseLifecyclePolicy.EPHEMERAL,
                      null,
                      NOW))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("EXTERNAL");
    }
  }

  @Nested
  @DisplayName("Retention and failures")
  class RetentionAndFailures {
    @Test
    @DisplayName("TTL retention records complete metadata and expiry cleanup is idempotent")
    void retentionExpiresIdempotently() {
      Fixture fixture = new Fixture();
      DatabaseLifecycleRequest request = request("run-1", DatabaseLifecyclePolicy.RETAIN_WITH_TTL);
      fixture.coordinator.prepare(request);
      fixture.coordinator.complete(request, ExecutionOutcome.FAILED, null);
      assertThat(fixture.evidence.audit.getFirst())
          .extracting(
              RetentionMetadata::executionId,
              RetentionMetadata::project,
              RetentionMetadata::environment,
              RetentionMetadata::gitRevision,
              RetentionMetadata::database,
              RetentionMetadata::migrationVersion,
              RetentionMetadata::owner,
              RetentionMetadata::retentionReason,
              RetentionMetadata::cleanupStatus)
          .containsExactly(
              "run-1",
              "project",
              "ci",
              "revision",
              "orders",
              "42",
              "owner",
              "failure analysis",
              CleanupStatus.RETAINED);
      assertThat(fixture.coordinator.expireRetained()).isEqualTo(1);
      assertThat(fixture.coordinator.expireRetained()).isZero();
      assertThat(fixture.operations.calls).containsOnlyOnce("cleanup:run-1");
    }

    @Test
    @DisplayName("Cleanup failure is audited and suppressed behind original failure")
    void cleanupFailurePreservesOriginal() {
      Fixture fixture = new Fixture();
      fixture.operations.failCleanup = true;
      DatabaseLifecycleRequest request = request("run-1", DatabaseLifecyclePolicy.EPHEMERAL);
      AssertionError original = new AssertionError("product failed");
      fixture.coordinator.prepare(request);
      fixture.coordinator.complete(request, ExecutionOutcome.FAILED, original);
      assertThat(original.getSuppressed())
          .singleElement()
          .isInstanceOf(DatabaseLifecycleException.class);
      assertThat(fixture.evidence.audit.getLast().cleanupStatus()).isEqualTo(CleanupStatus.FAILED);
    }
  }

  @Test
  @DisplayName("Parallel executions clean only their own namespace")
  void parallelIsolation() throws Exception {
    Fixture fixture = new Fixture();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          IntStream.range(0, 30)
              .mapToObj(
                  i ->
                      (java.util.concurrent.Callable<Void>)
                          () -> {
                            DatabaseLifecycleRequest request =
                                request("run-" + i, DatabaseLifecyclePolicy.EPHEMERAL);
                            fixture.coordinator.prepare(request);
                            fixture.coordinator.complete(request, ExecutionOutcome.PASSED, null);
                            return null;
                          })
              .toList();
      for (var future : executor.invokeAll(tasks)) future.get();
    }
    assertThat(fixture.operations.calls.stream().filter(v -> v.startsWith("cleanup:")).toList())
        .hasSize(30)
        .doesNotHaveDuplicates();
    assertThat(fixture.evidence.audit)
        .hasSize(30)
        .extracting(RetentionMetadata::executionId)
        .doesNotHaveDuplicates();
  }

  private static DatabaseLifecycleRequest request(
      String executionId, DatabaseLifecyclePolicy policy) {
    return new DatabaseLifecycleRequest(
        executionId,
        "project",
        "ci",
        "revision",
        "orders",
        "42",
        "owner",
        policy == DatabaseLifecyclePolicy.RETAIN_WITH_TTL ? "failure analysis" : "",
        policy,
        policy == DatabaseLifecyclePolicy.RETAIN_WITH_TTL ? Duration.ofMinutes(5) : null,
        NOW.minusSeconds(600));
  }

  private static final class Fixture {
    private final Operations operations = new Operations();
    private final Evidence evidence = new Evidence();
    private final DatabaseLifecycleCoordinator coordinator =
        new DatabaseLifecycleCoordinator(
            operations,
            value -> value.replace("secret", "[REDACTED]"),
            evidence,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static final class Operations implements DatabaseLifecycleOperations {
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean failCleanup;

    public void provision(DatabaseLifecycleRequest r) {
      calls.add("provision:" + r.executionId());
    }

    public void verifyReady(DatabaseLifecycleRequest r) {
      calls.add("ready:" + r.executionId());
    }

    public void migrate(DatabaseLifecycleRequest r) {
      calls.add("migrate:" + r.executionId());
    }

    public void reset(DatabaseLifecycleRequest r) {
      calls.add("reset:" + r.executionId());
    }

    public void seed(DatabaseLifecycleRequest r) {
      calls.add("seed:" + r.executionId());
    }

    public String exportSnapshot(DatabaseLifecycleRequest r) {
      calls.add("snapshot:" + r.executionId());
      return "secret";
    }

    public void cleanup(DatabaseLifecycleRequest r) {
      calls.add("cleanup:" + r.executionId());
      if (failCleanup) throw new IllegalStateException("physical identity must not escape");
    }
  }

  private static final class Evidence implements LifecycleEvidenceSink {
    private final List<String> snapshots = Collections.synchronizedList(new ArrayList<>());
    private final List<RetentionMetadata> audit = Collections.synchronizedList(new ArrayList<>());

    public void snapshot(String executionId, String database, String content) {
      snapshots.add(executionId + ":" + content);
    }

    public void audit(RetentionMetadata metadata) {
      audit.add(metadata);
    }
  }
}
