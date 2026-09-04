package com.codinglair.taf.mcp.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Durable job state machine")
class JobStateMachineTest {
  private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

  @Nested
  @DisplayName("Transition validation")
  class TransitionValidation {
    @Test
    @DisplayName("supports the successful lifecycle")
    void supportsSuccessfulLifecycle() {
      Job queued = job();
      Job running = JobStateMachine.transition(queued, JobState.RUNNING, NOW.plusSeconds(1));
      Job updated =
          JobStateMachine.progress(
              running, 40, "suite", "Completed safe suite boundary", NOW.plusSeconds(2));
      Job checkpointed = JobStateMachine.checkpoint(updated, "suite:checkout", NOW.plusSeconds(3));
      Job succeeded =
          JobStateMachine.succeed(
              checkpointed,
              List.of(new JobReference("taf://report/job-1", "application/json")),
              NOW.plusSeconds(4));

      assertThat(succeeded.state()).isEqualTo(JobState.SUCCEEDED);
      assertThat(succeeded.progressPercent()).isEqualTo(100);
      assertThat(succeeded.safeCheckpoint()).contains("suite:checkout");
      assertThat(succeeded.events()).extracting(JobEvent::sequence).containsExactly(1L);
      assertThat(succeeded.resultReferences()).hasSize(1);
    }

    @ParameterizedTest(name = "terminal state {0} is immutable")
    @EnumSource(
        value = JobState.class,
        names = {"SUCCEEDED", "FAILED", "CANCELLED"})
    @DisplayName("rejects every transition from a terminal state")
    void rejectsTransitionsFromTerminalState(JobState terminal) {
      Job terminalJob =
          JobStateMachine.transition(
              terminal == JobState.CANCELLED
                  ? JobStateMachine.transition(job(), JobState.CANCEL_REQUESTED, NOW)
                  : JobStateMachine.transition(job(), JobState.RUNNING, NOW),
              terminal,
              NOW);

      assertThatThrownBy(() -> JobStateMachine.transition(terminalJob, JobState.QUEUED, NOW))
          .isInstanceOf(InvalidJobTransitionException.class);
    }

    @Test
    @DisplayName("rejects invalid queued to succeeded transition")
    void rejectsInvalidTransition() {
      assertThatThrownBy(() -> JobStateMachine.transition(job(), JobState.SUCCEEDED, NOW))
          .isInstanceOf(InvalidJobTransitionException.class)
          .hasMessageContaining("QUEUED");
    }

    @Test
    @DisplayName("requires monotonic running progress")
    void requiresMonotonicRunningProgress() {
      Job running = JobStateMachine.transition(job(), JobState.RUNNING, NOW);
      Job progressed = JobStateMachine.progress(running, 60, "progress", "Bounded message", NOW);

      assertThatThrownBy(
              () -> JobStateMachine.progress(progressed, 59, "progress", "Regressed", NOW))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("Recovery semantics")
  class RecoverySemantics {
    @Test
    @DisplayName("marks interrupted execution pending at its last safe boundary")
    void marksInterruptedExecutionRecoveryPending() {
      Job running = JobStateMachine.transition(job(), JobState.RUNNING, NOW);
      Job checkpointed = JobStateMachine.checkpoint(running, "suite:one-complete", NOW);

      Job recovered = JobStateMachine.recoverAfterRestart(checkpointed, NOW.plusSeconds(1));

      assertThat(recovered.state()).isEqualTo(JobState.RECOVERY_PENDING);
      assertThat(recovered.safeCheckpoint()).contains("suite:one-complete");
    }
  }

  private static Job job() {
    return Job.queued(new JobId("job-1"), "execute", Map.of("project", "sample"), NOW);
  }
}
