package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.core.Capability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PreflightPolicyTest {
  private static final Capability DATABASE =
      new Capability("database", "Database access", Capability.CapabilityType.ENVIRONMENT);

  @Test
  void degradedEnvironmentRunsWithWarningByDefault() {
    PreflightResult result = result(EnvironmentStatus.DEGRADED, Optional.of(DATABASE));

    ExecutionDecision decision =
        DegradedEnvironmentPolicy.allowWithWarningsUnlessBlocked(Set.of()).evaluate(result);

    assertThat(decision.permitted()).isTrue();
    assertThat(decision.warning()).isTrue();
  }

  @Test
  void degradedAffectedCapabilityCanBeBlocked() {
    PreflightResult result = result(EnvironmentStatus.DEGRADED, Optional.of(DATABASE));

    ExecutionDecision decision =
        DegradedEnvironmentPolicy.allowWithWarningsUnlessBlocked(Set.of(DATABASE)).evaluate(result);

    assertThat(decision.permitted()).isFalse();
  }

  @Test
  void unavailableMisconfiguredAndUnknownAreBlocked() {
    for (EnvironmentStatus status :
        List.of(
            EnvironmentStatus.UNAVAILABLE,
            EnvironmentStatus.MISCONFIGURED,
            EnvironmentStatus.UNKNOWN)) {
      assertThat(
              DegradedEnvironmentPolicy.allowWithWarningsUnlessBlocked(Set.of())
                  .evaluate(result(status, Optional.empty()))
                  .permitted())
          .isFalse();
    }
  }

  private static PreflightResult result(EnvironmentStatus status, Optional<Capability> capability) {
    return PreflightResult.from(
        List.of(
            new PreflightCheckResult(
                "check",
                PreflightCheckType.READINESS,
                capability,
                status,
                "result",
                "correct it",
                Map.of(),
                Instant.now())));
  }
}
