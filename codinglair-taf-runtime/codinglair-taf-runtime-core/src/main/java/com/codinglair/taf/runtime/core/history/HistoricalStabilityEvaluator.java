package com.codinglair.taf.runtime.core.history;

import com.codinglair.taf.runtime.core.failure.StabilityStatus;
import java.util.List;

/** Deterministic compatibility-window policy: only completed pass/fail outcomes participate. */
public final class HistoricalStabilityEvaluator {
  public StabilityStatus evaluate(List<ExecutionAttemptSummary> history, int minimumSamples) {
    return evaluate(history, minimumSamples, CompatibilityScope.all());
  }

  public StabilityStatus evaluate(List<ExecutionAttemptSummary> history, int minimumSamples,
      CompatibilityScope compatibility) {
    if (minimumSamples < 2) throw new IllegalArgumentException("minimumSamples must be at least two");
    var completed = history.stream().filter(compatibility::includes)
        .filter(item -> item.outcome() == ExecutionAttemptSummary.Outcome.PASSED
            || item.outcome() == ExecutionAttemptSummary.Outcome.FAILED)
        .toList();
    if (completed.size() < minimumSamples) return StabilityStatus.INSUFFICIENT_HISTORY;
    boolean passed = completed.stream().anyMatch(item -> item.outcome() == ExecutionAttemptSummary.Outcome.PASSED);
    boolean failed = completed.stream().anyMatch(item -> item.outcome() == ExecutionAttemptSummary.Outcome.FAILED);
    return passed && failed ? StabilityStatus.HISTORICALLY_FLAKY : StabilityStatus.STABLE;
  }

  /** Explicit build/environment compatibility boundary; empty sets mean unrestricted. */
  public record CompatibilityScope(java.util.Set<String> buildIds, java.util.Set<String> environmentIds) {
    public CompatibilityScope {
      buildIds = java.util.Set.copyOf(buildIds);
      environmentIds = java.util.Set.copyOf(environmentIds);
    }
    public static CompatibilityScope all() { return new CompatibilityScope(java.util.Set.of(), java.util.Set.of()); }
    boolean includes(ExecutionAttemptSummary item) {
      return (buildIds.isEmpty() || buildIds.contains(item.buildId()))
          && (environmentIds.isEmpty() || environmentIds.contains(item.environmentId()));
    }
  }

  public boolean recurring(List<ExecutionAttemptSummary> history, String signature, int minimumOccurrences) {
    if (minimumOccurrences < 2) throw new IllegalArgumentException("minimumOccurrences must be at least two");
    return history.stream().filter(item -> item.signature() != null)
        .filter(item -> item.signature().value().equals(signature)).limit(minimumOccurrences).count()
        >= minimumOccurrences;
  }
}
