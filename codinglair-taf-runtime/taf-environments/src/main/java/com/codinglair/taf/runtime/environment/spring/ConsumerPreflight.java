package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import com.codinglair.taf.runtime.environment.PreflightCheckResult;
import com.codinglair.taf.runtime.environment.PreflightCheckType;
import com.codinglair.taf.runtime.environment.PreflightResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Compatibility facade for the Runtime-wide preflight service.
 *
 * @deprecated inject {@link com.codinglair.taf.runtime.core.preflight.ConsumerPreflight} at new
 *     execution boundaries.
 */
@Deprecated(forRemoval = false, since = "1.0")
public final class ConsumerPreflight {
  private final com.codinglair.taf.runtime.core.preflight.ConsumerPreflight delegate;

  public ConsumerPreflight(com.codinglair.taf.runtime.core.preflight.ConsumerPreflight delegate) {
    this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
  }

  /**
   * @deprecated retained for source compatibility; Spring consumers should inject the core service.
   */
  @Deprecated(forRemoval = false, since = "1.0")
  public ConsumerPreflight(
      ConsumerConfigurationProperties configuration,
      SecretReferenceAvailability secrets,
      java.util.List<ConsumerPreflightContributor> contributors) {
    throw new UnsupportedOperationException(
        "Direct ConsumerPreflight construction is no longer supported; inject the Runtime Core ConsumerPreflight bean");
  }

  public PreflightResult inspect() {
    return PreflightResult.from(
        delegate.inspect().diagnostics().stream()
            .map(
                diagnostic ->
                    new PreflightCheckResult(
                        diagnostic.checkId(),
                        PreflightCheckType.CUSTOM,
                        Optional.empty(),
                        EnvironmentStatus.MISCONFIGURED,
                        diagnostic.message(),
                        diagnostic.correctiveAction(),
                        Map.copyOf(diagnostic.context()),
                        Instant.now()))
            .toList());
  }

  public void verify() {
    try {
      delegate.verify();
    } catch (com.codinglair.taf.runtime.core.preflight.ConsumerPreflightException failure) {
      throw new ConsumerPreflightException(result(failure.diagnostics()));
    }
  }

  private static PreflightResult result(
      java.util.List<com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic> diagnostics) {
    return PreflightResult.from(
        diagnostics.stream()
            .map(
                diagnostic ->
                    new PreflightCheckResult(
                        diagnostic.checkId(),
                        PreflightCheckType.CUSTOM,
                        Optional.empty(),
                        EnvironmentStatus.MISCONFIGURED,
                        diagnostic.message(),
                        diagnostic.correctiveAction(),
                        Map.copyOf(diagnostic.context()),
                        Instant.now()))
            .toList());
  }
}
