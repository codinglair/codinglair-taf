package com.codinglair.taf.runtime.core.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.failure.FailureClassificationService;
import com.codinglair.taf.runtime.core.failure.FailureContext;
import com.codinglair.taf.runtime.core.failure.FailureSignatureService;
import com.codinglair.taf.runtime.core.failure.StabilityStatus;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttemptHistoryServiceTest {
  @TempDir Path root;

  @Test
  void successfulAndFailedRunsProduceHistoricalFlakyWithoutRewritingRootCauseOrSignature() {
    var service = service(repository(), configuration(), 2);
    var passed = service.complete(descriptor("run-1", "attempt-1", ExecutionAttemptSummary.Outcome.PASSED, null));
    var failed = service.complete(descriptor("run-2", "attempt-1", ExecutionAttemptSummary.Outcome.FAILED,
        new AssertionError("synthetic mismatch")));
    assertThat(passed.analysis()).isNull();
    assertThat(failed.analysis().stability()).isEqualTo(StabilityStatus.HISTORICALLY_FLAKY);
    assertThat(failed.analysis().classification().type()).isEqualTo(com.codinglair.taf.core.Error.ErrorType.INCONCLUSIVE);
    assertThat(failed.analysis().signature()).isNotNull();
    assertThat(repository().findByTest("project", "test", 10, Duration.ofDays(1)).records()).hasSize(2);
  }

  @Test
  void failureOnlyRunsAreRecurringButNotFlakyAndRerunsRemainSeparate() {
    var repository = repository();
    var service = service(repository, configuration(), 2);
    var first = service.complete(descriptor("run", "attempt-1", ExecutionAttemptSummary.Outcome.FAILED,
        failure())).analysis();
    var second = service.complete(descriptor("run", "attempt-2", ExecutionAttemptSummary.Outcome.FAILED,
        failure())).analysis();
    var records = repository.findByTest("project", "test", 10, Duration.ofDays(1)).records();
    assertThat(records).extracting(ExecutionAttemptSummary::attemptId).containsExactlyInAnyOrder("attempt-1", "attempt-2");
    assertThat(second.stability()).isEqualTo(StabilityStatus.STABLE);
    assertThat(new HistoricalStabilityEvaluator().recurring(records, first.signature().value(), 2)).isTrue();
  }

  @Test
  void unavailableOptionalRepositoryLeavesCurrentAttemptAuthoritative() {
    ExecutionHistoryRepository unavailable = new ExecutionHistoryRepository() {
      public HistoryResult record(ExecutionAttemptSummary summary) { return new HistoryResult(HistoryResult.Status.UNAVAILABLE, List.of(), "offline"); }
      public HistoryResult findByTest(String p, String t, int n, Duration a) { return new HistoryResult(HistoryResult.Status.UNAVAILABLE, List.of(), "offline"); }
      public HistoryResult findBySignature(String p, String s, int n, Duration a) { return new HistoryResult(HistoryResult.Status.UNAVAILABLE, List.of(), "offline"); }
    };
    var completion = service(unavailable, configuration(), 2).complete(
        descriptor("run", "attempt", ExecutionAttemptSummary.Outcome.FAILED, failure()));
    assertThat(completion.analysis().historyStatus()).isEqualTo(com.codinglair.taf.runtime.core.failure.FailureAnalysis.HistoryStatus.UNAVAILABLE);
    assertThat(completion.summary().outcome()).isEqualTo(ExecutionAttemptSummary.Outcome.FAILED);
    assertThat(completion.analysis().classification()).isNotNull();
  }

  private FileExecutionHistoryRepository repository() { return new FileExecutionHistoryRepository(configuration()); }
  private HistoryConfiguration configuration() { return new HistoryConfiguration(true, root, 20, Duration.ofDays(1), 1_000_000,
      HistoryConfiguration.UnavailabilityPolicy.CONTINUE, HistoryConfiguration.CorruptionPolicy.REPORT); }
  private static AttemptHistoryService service(ExecutionHistoryRepository repository, HistoryConfiguration configuration, int samples) {
    return new AttemptHistoryService(new FailureClassificationService(), new FailureSignatureService(), repository, configuration, samples);
  }
  private static AttemptHistoryService.AttemptDescriptor descriptor(String execution, String attempt,
      ExecutionAttemptSummary.Outcome outcome, Throwable failure) {
    return new AttemptHistoryService.AttemptDescriptor("project", "test", execution, attempt, Instant.now(), outcome,
        Duration.ofMillis(1), "build", "local", "web", "action", FailureContext.Boundary.UNKNOWN,
        failure, List.of());
  }
  private static RuntimeException failure() {
    RuntimeException failure = new RuntimeException("same failure");
    failure.setStackTrace(new StackTraceElement[] {new StackTraceElement("Example", "run", "Example.java", 1)});
    return failure;
  }
}
