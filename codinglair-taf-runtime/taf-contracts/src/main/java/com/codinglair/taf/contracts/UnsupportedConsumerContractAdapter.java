package com.codinglair.taf.contracts;

import java.util.List;

/** Explicit placeholder used until a consumer-contract provider is approved and installed. */
public final class UnsupportedConsumerContractAdapter implements ConsumerContractAdapter {
  @Override
  public String provider() {
    return "taf-consumer-contract-boundary";
  }

  @Override
  public ContractValidationResult validate(ContractDocument document) {
    return new ContractValidationResult(
        ContractValidationResult.Status.UNSUPPORTED, provider(), List.of(gap()));
  }

  @Override
  public ContractScaffoldResult scaffold(ContractScaffoldRequest request) {
    return new ContractScaffoldResult(
        ContractValidationResult.Status.UNSUPPORTED, provider(), List.of(), List.of(gap()));
  }

  private static ContractDiagnostic gap() {
    return ContractDiagnostic.error(
        "CONSUMER_PROVIDER_REQUIRED",
        "$",
        "Consumer-contract validation requires an approved provider",
        "Register a ConsumerContractAdapter such as an approved Pact adapter");
  }
}
