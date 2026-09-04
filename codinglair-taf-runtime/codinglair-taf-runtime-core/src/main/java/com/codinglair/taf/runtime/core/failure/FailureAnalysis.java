package com.codinglair.taf.runtime.core.failure;

import java.util.Objects;

/** Authoritative root-cause, signature, stability and history state propagated to adapters. */
public record FailureAnalysis(
    FailureClassification classification,
    FailureSignature signature,
    StabilityStatus stability,
    HistoryStatus historyStatus) {
  public FailureAnalysis {
    Objects.requireNonNull(classification, "classification");
    Objects.requireNonNull(stability, "stability");
    Objects.requireNonNull(historyStatus, "historyStatus");
  }

  public static FailureAnalysis classify(String capability, String phase, FailureContext.Boundary boundary, Throwable failure) {
    FailureContext context = FailureContext.of(capability, phase, boundary, failure);
    return new FailureAnalysis(new FailureClassificationService().classify(context),
        new FailureSignatureService().sign(context), StabilityStatus.INSUFFICIENT_HISTORY,
        HistoryStatus.DISABLED);
  }

  public enum HistoryStatus { SUCCESS, DISABLED, UNAVAILABLE, CORRUPT }
}
