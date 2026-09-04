package com.codinglair.taf.runtime.core.history;

import java.time.Duration;

/** Optional cross-run history SPI. Repository failure must not mutate current-run results. */
public interface ExecutionHistoryRepository {
  HistoryResult record(ExecutionAttemptSummary summary);

  HistoryResult findByTest(String projectId, String testId, int maximumRecords, Duration maximumAge);

  HistoryResult findBySignature(String projectId, String signature, int maximumRecords, Duration maximumAge);
}
