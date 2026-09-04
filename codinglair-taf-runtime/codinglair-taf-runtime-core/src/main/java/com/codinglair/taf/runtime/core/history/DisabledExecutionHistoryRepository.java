package com.codinglair.taf.runtime.core.history;

import java.time.Duration;
import java.util.List;

public final class DisabledExecutionHistoryRepository implements ExecutionHistoryRepository {
  private static final HistoryResult DISABLED = new HistoryResult(HistoryResult.Status.DISABLED, List.of(), "history disabled");
  public HistoryResult record(ExecutionAttemptSummary summary) { return DISABLED; }
  public HistoryResult findByTest(String projectId, String testId, int maximumRecords, Duration maximumAge) { return DISABLED; }
  public HistoryResult findBySignature(String projectId, String signature, int maximumRecords, Duration maximumAge) { return DISABLED; }
}
