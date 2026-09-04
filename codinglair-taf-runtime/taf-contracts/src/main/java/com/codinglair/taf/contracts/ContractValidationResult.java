package com.codinglair.taf.contracts;

import java.util.List;
import java.util.Objects;

/** Validation outcome that distinguishes invalid input, capability gaps, and provider failures. */
public record ContractValidationResult(
    Status status, String provider, List<ContractDiagnostic> diagnostics) {
  public enum Status {
    VALID,
    INVALID,
    UNSUPPORTED,
    PROVIDER_FAILURE
  }

  public ContractValidationResult {
    status = Objects.requireNonNull(status, "status must not be null");
    if (provider == null || provider.isBlank())
      throw new IllegalArgumentException("provider must not be blank");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    if (status != Status.VALID && diagnostics.isEmpty()) {
      throw new IllegalArgumentException("A non-valid result requires a diagnostic");
    }
  }

  public boolean valid() {
    return status == Status.VALID;
  }
}
