package com.codinglair.taf.runtime.core.failure;

import com.codinglair.taf.core.Error.ErrorType;
import java.util.Objects;

/** Authoritative, sanitized root-cause decision for an execution attempt. */
public record FailureClassification(ErrorType type, Source source, String reason) {
  public FailureClassification {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(source, "source");
    reason = new com.codinglair.taf.runtime.core.reporting.RedactionPipeline()
        .redact(Objects.requireNonNull(reason, "reason")).strip();
    if (reason.isEmpty() || reason.length() > 512) {
      throw new IllegalArgumentException("reason must contain 1-512 characters");
    }
  }

  public enum Source {
    EXPLICIT,
    RUNTIME_RULE,
    HISTORICAL_DERIVATION,
    ACCEPTED_EXTERNAL_SUGGESTION,
    UNCLASSIFIED
  }
}
