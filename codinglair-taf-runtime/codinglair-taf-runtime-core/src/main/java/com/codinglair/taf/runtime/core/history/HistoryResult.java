package com.codinglair.taf.runtime.core.history;

import java.util.List;
import java.util.Objects;

public record HistoryResult(Status status, List<ExecutionAttemptSummary> records, String diagnostic) {
  public HistoryResult {
    Objects.requireNonNull(status, "status");
    records = List.copyOf(Objects.requireNonNull(records, "records"));
    diagnostic = diagnostic == null ? "" : diagnostic;
  }

  public enum Status { SUCCESS, DISABLED, UNAVAILABLE, CORRUPT }
}
