package com.codinglair.taf.runtime.core.history;

import com.codinglair.taf.core.Error.ErrorType;
import com.codinglair.taf.runtime.core.failure.FailureClassification;
import com.codinglair.taf.runtime.core.failure.FailureSignature;
import com.codinglair.taf.runtime.core.failure.StabilityStatus;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/** Child-JVM writer for cross-process atomicity verification. */
public final class FileHistoryProcessProbe {
  private FileHistoryProcessProbe() {}
  public static void main(String[] arguments) {
    var configuration = new HistoryConfiguration(true, Path.of(arguments[0]), 100, Duration.ofDays(1),
        10_000_000, HistoryConfiguration.UnavailabilityPolicy.REQUIRE_HISTORY,
        HistoryConfiguration.CorruptionPolicy.REPORT);
    var summary = new ExecutionAttemptSummary(1, "project", "test", "execution", arguments[1], Instant.now(),
        ExecutionAttemptSummary.Outcome.FAILED,
        new FailureClassification(ErrorType.AUTOMATION_FAILURE, FailureClassification.Source.RUNTIME_RULE, "runner boundary"),
        StabilityStatus.INSUFFICIENT_HISTORY,
        new FailureSignature("failure-signature:v1:" + "c".repeat(64), "v1"), Duration.ofMillis(1), "build", "local");
    if (new FileExecutionHistoryRepository(configuration).record(summary).status() != HistoryResult.Status.SUCCESS) System.exit(2);
  }
}
