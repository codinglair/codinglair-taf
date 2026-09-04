package com.codinglair.taf.runtime.core.failure;

import com.codinglair.taf.core.Error.ErrorType;
import java.util.List;
import java.util.Objects;

/** Bounded structured input to Runtime classification and signature policies. */
public record FailureContext(
    String capability,
    String phase,
    Boundary boundary,
    Throwable failure,
    List<ErrorType> authoritativeClassifications) {
  public FailureContext {
    capability = bounded(capability, "unknown", 128);
    phase = bounded(phase, "unknown", 128);
    boundary = Objects.requireNonNullElse(boundary, Boundary.UNKNOWN);
    authoritativeClassifications =
        List.copyOf(Objects.requireNonNullElse(authoritativeClassifications, List.of()));
    if (authoritativeClassifications.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("authoritative classifications must not contain null");
    }
  }

  public static FailureContext of(String capability, String phase, Boundary boundary, Throwable failure) {
    return new FailureContext(capability, phase, boundary, failure, List.of());
  }

  private static String bounded(String value, String fallback, int limit) {
    String normalized = value == null || value.isBlank() ? fallback : value.strip();
    return normalized.substring(0, Math.min(limit, normalized.length()));
  }

  public enum Boundary {
    PRODUCT,
    AUTOMATION,
    ENVIRONMENT,
    TEST_DATA,
    REQUIREMENT,
    UNKNOWN
  }
}
