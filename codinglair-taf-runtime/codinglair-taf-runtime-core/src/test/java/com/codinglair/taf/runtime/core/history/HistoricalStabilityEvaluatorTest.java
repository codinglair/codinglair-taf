package com.codinglair.taf.runtime.core.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.core.Error.ErrorType;
import com.codinglair.taf.runtime.core.failure.FailureClassification;
import com.codinglair.taf.runtime.core.failure.FailureSignature;
import com.codinglair.taf.runtime.core.failure.StabilityStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalStabilityEvaluatorTest {
  private final HistoricalStabilityEvaluator evaluator = new HistoricalStabilityEvaluator();

  @Test
  void passAndFailureAreFlakyWhileFailureOnlyIsStableRecurring() {
    var failedOne = summary("a1", ExecutionAttemptSummary.Outcome.FAILED);
    var failedTwo = summary("a2", ExecutionAttemptSummary.Outcome.FAILED);
    var passed = summary("a3", ExecutionAttemptSummary.Outcome.PASSED);
    assertThat(evaluator.evaluate(List.of(failedOne), 2)).isEqualTo(StabilityStatus.INSUFFICIENT_HISTORY);
    assertThat(evaluator.evaluate(List.of(failedOne, failedTwo), 2)).isEqualTo(StabilityStatus.STABLE);
    assertThat(evaluator.recurring(List.of(failedOne, failedTwo), failedOne.signature().value(), 2)).isTrue();
    assertThat(evaluator.evaluate(List.of(failedOne, passed), 2)).isEqualTo(StabilityStatus.HISTORICALLY_FLAKY);
    assertThat(failedOne.classification().type()).isEqualTo(ErrorType.AUTOMATION_FAILURE);
    assertThat(evaluator.evaluate(List.of(passed, summary("a4", ExecutionAttemptSummary.Outcome.PASSED)), 2))
        .isEqualTo(StabilityStatus.STABLE);
  }

  @Test
  void incompatibleBuildAndEnvironmentDoNotCreateFlakyStatusOrRewriteCurrentResult() {
    var failed = summary("failed", ExecutionAttemptSummary.Outcome.FAILED);
    var incompatiblePass = new ExecutionAttemptSummary(1, "project", "test", "execution", "pass",
        Instant.EPOCH, ExecutionAttemptSummary.Outcome.PASSED, null, StabilityStatus.INSUFFICIENT_HISTORY,
        null, Duration.ZERO, "other-build", "other-env");
    var scope = new HistoricalStabilityEvaluator.CompatibilityScope(java.util.Set.of("build"), java.util.Set.of("local"));
    assertThat(evaluator.evaluate(List.of(failed, incompatiblePass), 2, scope))
        .isEqualTo(StabilityStatus.INSUFFICIENT_HISTORY);
    assertThat(failed.outcome()).isEqualTo(ExecutionAttemptSummary.Outcome.FAILED);
    assertThat(failed.classification().type()).isEqualTo(ErrorType.AUTOMATION_FAILURE);
    assertThat(failed.signature().value()).endsWith("a".repeat(64));
  }

  private ExecutionAttemptSummary summary(String attempt, ExecutionAttemptSummary.Outcome outcome) {
    var classification = outcome == ExecutionAttemptSummary.Outcome.PASSED ? null
        : new FailureClassification(ErrorType.AUTOMATION_FAILURE,
            FailureClassification.Source.RUNTIME_RULE, "runner boundary");
    var signature = outcome == ExecutionAttemptSummary.Outcome.PASSED ? null
        : new FailureSignature("failure-signature:v1:" + "a".repeat(64), "v1");
    return new ExecutionAttemptSummary(1, "project", "test", "execution", attempt, Instant.EPOCH,
        outcome, classification, StabilityStatus.INSUFFICIENT_HISTORY, signature, Duration.ofMillis(1),
        "build", "local");
  }
}
