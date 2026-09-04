package com.codinglair.taf.runtime.core.preflight;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Single Runtime-wide aggregator invoked at controlled consumer execution boundaries. */
public final class ConsumerPreflight {
  private final List<ConsumerPreflightContributor> contributors;

  public ConsumerPreflight(List<ConsumerPreflightContributor> contributors) {
    this.contributors = List.copyOf(Objects.requireNonNull(contributors, "contributors"));
  }

  public ConsumerPreflightResult inspect() {
    List<PreflightDiagnostic> diagnostics = new ArrayList<>();
    for (int index = 0; index < contributors.size(); index++) {
      try {
        diagnostics.addAll(List.copyOf(contributors.get(index).inspect()));
      } catch (RuntimeException failure) {
        diagnostics.add(
            new PreflightDiagnostic(
                "runtime.contributor." + index,
                "Preflight contributor could not complete its checks",
                "Inspect the contributor configuration and sanitized diagnostics"));
      }
    }
    return new ConsumerPreflightResult(diagnostics);
  }

  public void verify() {
    ConsumerPreflightResult result = inspect();
    if (!result.passed()) throw new ConsumerPreflightException(result.diagnostics());
  }
}
