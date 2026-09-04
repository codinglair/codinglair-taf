package com.codinglair.taf.mcp.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Local durable job repository")
class LocalJobRepositoryTest {
  private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
  @TempDir Path root;

  @Nested
  @DisplayName("Persistence and recovery")
  class PersistenceAndRecovery {
    @Test
    @DisplayName("survives repository restart and recovers only from a safe boundary")
    void survivesRestartAndRecoversSafely() {
      JobRepository first = new LocalJobRepository(root);
      Job queued = first.create(job("restart"));
      Job running =
          first.save(
              JobStateMachine.transition(queued, JobState.RUNNING, NOW.plusSeconds(1)),
              queued.version());
      Job checkpointed =
          first.save(
              JobStateMachine.checkpoint(running, "suite:payments-complete", NOW.plusSeconds(2)),
              running.version());

      JobService restarted =
          new JobService(
              new LocalJobRepository(root), Clock.fixed(NOW.plusSeconds(3), ZoneOffset.UTC));
      List<Job> recovered = restarted.recoverAfterRestart();

      assertThat(recovered)
          .singleElement()
          .satisfies(
              job -> {
                assertThat(job.state()).isEqualTo(JobState.RECOVERY_PENDING);
                assertThat(job.safeCheckpoint()).contains("suite:payments-complete");
              });
    }

    @Test
    @DisplayName("rejects corrupt persisted state with a sanitized failure")
    void rejectsCorruptStateWithSanitizedFailure() throws Exception {
      JobRepository repository = new LocalJobRepository(root);
      Job job = repository.create(job("corrupt"));
      Path persisted =
          Files.list(root)
              .filter(path -> path.toString().endsWith(".job"))
              .findFirst()
              .orElseThrow();
      Files.write(persisted, new byte[] {1, 2, 3});

      assertThatThrownBy(() -> repository.find(job.id()))
          .isInstanceOf(JobPersistenceException.class)
          .hasMessageNotContaining("sample");
    }
  }

  @Nested
  @DisplayName("Concurrency")
  class Concurrency {
    @Test
    @DisplayName("rejects stale updates rather than losing state")
    void rejectsStaleUpdates() {
      JobRepository repository = new LocalJobRepository(root);
      Job original = repository.create(job("conflict"));
      Job first = JobStateMachine.transition(original, JobState.RUNNING, NOW);
      repository.save(first, original.version());

      Job stale = JobStateMachine.transition(original, JobState.CANCEL_REQUESTED, NOW);
      assertThatThrownBy(() -> repository.save(stale, original.version()))
          .isInstanceOf(JobConflictException.class);
    }

    @Test
    @DisplayName("preserves append-only progress history")
    void preservesAppendOnlyProgressHistory() {
      JobRepository repository = new LocalJobRepository(root);
      Job queued = repository.create(job("history"));
      Job running =
          repository.save(
              JobStateMachine.transition(queued, JobState.RUNNING, NOW), queued.version());
      Job progressed =
          repository.save(
              JobStateMachine.progress(running, 10, "progress", "first", NOW), running.version());
      Job forged =
          new Job(
              progressed.id(),
              progressed.operation(),
              progressed.payload(),
              progressed.state(),
              20,
              progressed.safeCheckpoint(),
              progressed.resultReferences(),
              List.of(new JobEvent(1, NOW, "progress", "replacement")),
              progressed.version() + 1,
              progressed.createdAt(),
              NOW.plusSeconds(1));

      assertThatThrownBy(() -> repository.save(forged, progressed.version()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("repository rejects forged mutation of a terminal job")
    void repositoryRejectsForgedTerminalMutation() {
      JobRepository repository = new LocalJobRepository(root);
      Job queued = repository.create(job("terminal"));
      Job running =
          repository.save(
              JobStateMachine.transition(queued, JobState.RUNNING, NOW), queued.version());
      Job succeeded =
          repository.save(JobStateMachine.succeed(running, List.of(), NOW), running.version());
      Job forged =
          new Job(
              succeeded.id(),
              succeeded.operation(),
              succeeded.payload(),
              JobState.RUNNING,
              100,
              succeeded.safeCheckpoint(),
              succeeded.resultReferences(),
              succeeded.events(),
              succeeded.version() + 1,
              succeeded.createdAt(),
              NOW.plusSeconds(1));

      assertThatThrownBy(() -> repository.save(forged, succeeded.version()))
          .isInstanceOf(InvalidJobTransitionException.class);
    }

    @Test
    @DisplayName("concurrent cancellation converges idempotently")
    void concurrentCancellationIsIdempotent() throws Exception {
      JobRepository writer = new LocalJobRepository(root);
      Job original = writer.create(job("cancel"));
      writer.save(JobStateMachine.transition(original, JobState.RUNNING, NOW), original.version());
      JobService first = new JobService(writer, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
      JobService second =
          new JobService(
              new LocalJobRepository(root), Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
      var failures = new ArrayList<Throwable>();
      var states = new ArrayList<JobState>();
      CountDownLatch start = new CountDownLatch(1);

      try (var executor = Executors.newFixedThreadPool(8)) {
        for (int index = 0; index < 8; index++) {
          JobService selected = index % 2 == 0 ? first : second;
          executor.submit(
              () -> {
                try {
                  start.await();
                  JobState state = selected.cancel(original.id()).state();
                  synchronized (states) {
                    states.add(state);
                  }
                } catch (Throwable failure) {
                  synchronized (failures) {
                    failures.add(failure);
                  }
                }
              });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      }

      assertThat(failures).isEmpty();
      assertThat(states).hasSize(8).containsOnly(JobState.CANCEL_REQUESTED);
      assertThat(writer.find(original.id()).orElseThrow().state())
          .isEqualTo(JobState.CANCEL_REQUESTED);
    }
  }

  @Nested
  @DisplayName("Bounds and references")
  class BoundsAndReferences {
    @Test
    @DisplayName("rejects payloads over the configured bound")
    void rejectsLargePayload() {
      JobRepository repository = new LocalJobRepository(root, new JobLimits(2, 10, 2, 1));
      Job oversized =
          Job.queued(new JobId("large"), "execute", Map.of("project", "far-too-large"), NOW);

      assertThatThrownBy(() -> repository.create(oversized))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("payload");
    }

    @Test
    @DisplayName("accepts controlled references and rejects inline or external locations")
    void acceptsOnlyControlledReferences() {
      assertThat(new JobReference("taf://artifact/job-1/result.zip", "application/zip").uri())
          .startsWith("taf://artifact/");
      assertThatThrownBy(
              () -> new JobReference("https://example.test/result.zip", "application/zip"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bounds the persisted progress event log")
    void boundsEventLog() {
      JobRepository repository = new LocalJobRepository(root, new JobLimits(2, 100, 1, 1));
      Job queued = repository.create(job("events"));
      Job running =
          repository.save(
              JobStateMachine.transition(queued, JobState.RUNNING, NOW), queued.version());
      Job first = JobStateMachine.progress(running, 10, "progress", "one", NOW);
      Job second = JobStateMachine.progress(first, 20, "progress", "two", NOW);

      assertThatThrownBy(() -> repository.save(second, running.version()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("limit");
    }
  }

  private static Job job(String id) {
    return Job.queued(new JobId(id), "execute", Map.of("project", "sample"), NOW);
  }
}
