package com.codinglair.taf.runtime.core.failure;

import com.codinglair.taf.core.Error.ErrorType;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import java.util.LinkedHashSet;

/** Dependency-light authoritative Runtime classification policy. */
public final class FailureClassificationService {
  private final RedactionPipeline redaction;

  public FailureClassificationService() {
    this(new RedactionPipeline());
  }

  public FailureClassificationService(RedactionPipeline redaction) {
    this.redaction = redaction;
  }

  public FailureClassification classify(FailureContext context) {
    var explicit = new LinkedHashSet<>(context.authoritativeClassifications());
    if (explicit.size() > 1) {
      throw new IllegalArgumentException("Contradictory authoritative failure classifications");
    }
    if (!explicit.isEmpty()) {
      return result(explicit.getFirst(), FailureClassification.Source.EXPLICIT, "approved explicit classification");
    }
    ErrorType type = switch (context.boundary()) {
      case PRODUCT -> ErrorType.PRODUCT_DEFECT;
      case AUTOMATION -> ErrorType.AUTOMATION_FAILURE;
      case ENVIRONMENT -> ErrorType.ENVIRONMENT_ISSUE;
      case TEST_DATA -> ErrorType.TEST_DATA_ISSUE;
      case REQUIREMENT -> ErrorType.REQUIREMENT_AMBIGUITY;
      case UNKNOWN -> ErrorType.INCONCLUSIVE;
    };
    var source = type == ErrorType.INCONCLUSIVE
        ? FailureClassification.Source.UNCLASSIFIED
        : FailureClassification.Source.RUNTIME_RULE;
    String reason = type == ErrorType.INCONCLUSIVE
        ? "no approved deterministic boundary mapping"
        : "failure established at " + context.boundary().name().toLowerCase() + " boundary";
    return result(type, source, reason);
  }

  private FailureClassification result(ErrorType type, FailureClassification.Source source, String reason) {
    return new FailureClassification(type, source, redaction.redact(reason));
  }
}
