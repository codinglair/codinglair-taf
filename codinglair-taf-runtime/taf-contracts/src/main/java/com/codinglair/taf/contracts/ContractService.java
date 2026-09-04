package com.codinglair.taf.contracts;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Thread-safe dispatcher that isolates adapter failures from callers and other providers. */
public final class ContractService {
  private final Map<ContractFormat, ContractAdapter> adapters;

  public ContractService(List<? extends ContractAdapter> adapters) {
    Objects.requireNonNull(adapters, "adapters must not be null");
    var indexed = new EnumMap<ContractFormat, ContractAdapter>(ContractFormat.class);
    for (var adapter : adapters) {
      Objects.requireNonNull(adapter, "adapter must not be null");
      var previous = indexed.put(adapter.format(), adapter);
      if (previous != null)
        throw new IllegalArgumentException("Duplicate adapter for " + adapter.format());
    }
    this.adapters = Map.copyOf(indexed);
  }

  public ContractValidationResult validate(ContractDocument document) {
    Objects.requireNonNull(document, "document must not be null");
    var adapter = adapters.get(document.format());
    if (adapter == null) return unsupportedValidation(document.format());
    try {
      return adapter.validate(document);
    } catch (RuntimeException failure) {
      return providerFailure(adapter.provider(), failure);
    }
  }

  public ContractScaffoldResult scaffold(ContractScaffoldRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    var adapter = adapters.get(request.document().format());
    if (adapter == null) {
      var diagnostic = capabilityGap(request.document().format());
      return new ContractScaffoldResult(
          ContractValidationResult.Status.UNSUPPORTED, "none", List.of(), List.of(diagnostic));
    }
    try {
      return adapter.scaffold(request);
    } catch (RuntimeException failure) {
      var diagnostic = providerFailureDiagnostic(adapter.provider(), failure);
      return new ContractScaffoldResult(
          ContractValidationResult.Status.PROVIDER_FAILURE,
          adapter.provider(),
          List.of(),
          List.of(diagnostic));
    }
  }

  private static ContractValidationResult unsupportedValidation(ContractFormat format) {
    return new ContractValidationResult(
        ContractValidationResult.Status.UNSUPPORTED, "none", List.of(capabilityGap(format)));
  }

  private static ContractValidationResult providerFailure(
      String provider, RuntimeException failure) {
    return new ContractValidationResult(
        ContractValidationResult.Status.PROVIDER_FAILURE,
        provider,
        List.of(providerFailureDiagnostic(provider, failure)));
  }

  private static ContractDiagnostic capabilityGap(ContractFormat format) {
    return ContractDiagnostic.error(
        "CAPABILITY_UNAVAILABLE",
        "$",
        "No contract adapter is registered for " + format,
        "Install and register an approved " + format + " contract adapter");
  }

  private static ContractDiagnostic providerFailureDiagnostic(
      String provider, RuntimeException failure) {
    return ContractDiagnostic.error(
        "PROVIDER_FAILURE",
        "$",
        "Contract provider '" + provider + "' failed (" + failure.getClass().getSimpleName() + ")",
        "Inspect the provider configuration and retry; the provider message was intentionally omitted");
  }
}
