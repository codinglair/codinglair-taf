package com.codinglair.taf.contracts;

import java.util.List;
import java.util.Objects;

/** Scaffold outcome with the same explicit failure categories as validation. */
public record ContractScaffoldResult(
    ContractValidationResult.Status status,
    String provider,
    List<GeneratedContractAsset> assets,
    List<ContractDiagnostic> diagnostics) {
  public ContractScaffoldResult {
    status = Objects.requireNonNull(status, "status must not be null");
    if (provider == null || provider.isBlank())
      throw new IllegalArgumentException("provider must not be blank");
    assets = List.copyOf(Objects.requireNonNull(assets, "assets must not be null"));
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    if (status == ContractValidationResult.Status.VALID && assets.isEmpty()) {
      throw new IllegalArgumentException("A successful scaffold requires an asset");
    }
    if (status != ContractValidationResult.Status.VALID && diagnostics.isEmpty()) {
      throw new IllegalArgumentException("An unsuccessful scaffold requires a diagnostic");
    }
  }
}
