package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.core.Capability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PreflightRunnerTest {
  private static final Capability DATABASE =
      new Capability("database", "Database", Capability.CapabilityType.ENVIRONMENT);
  private static final Capability KAFKA =
      new Capability("kafka", "Kafka", Capability.CapabilityType.ENVIRONMENT);

  @Test
  void runsSuiteAndRequestedCapabilityChecksOnly() {
    EnvironmentRequest request =
        new EnvironmentRequest(
            "db",
            new EnvironmentType("integration"),
            EnvironmentMode.EXTERNAL,
            java.util.Set.of(DATABASE),
            Map.of(),
            java.time.Duration.ofSeconds(1));

    PreflightResult result =
        new PreflightRunner()
            .run(
                request,
                List.of(
                    check("suite", Optional.empty(), EnvironmentStatus.READY),
                    check("database", Optional.of(DATABASE), EnvironmentStatus.READY),
                    check("kafka", Optional.of(KAFKA), EnvironmentStatus.UNAVAILABLE)));

    assertThat(result.status()).isEqualTo(EnvironmentStatus.READY);
    assertThat(result.checks())
        .extracting(PreflightCheckResult::checkId)
        .containsExactly("suite", "database");
  }

  @Test
  void thrownCheckBecomesSanitizedUnknownDiagnostic() {
    PreflightCheck throwing =
        new PreflightCheck() {
          public String id() {
            return "auth";
          }

          public PreflightCheckType type() {
            return PreflightCheckType.AUTHENTICATION;
          }

          public Optional<Capability> capability() {
            return Optional.empty();
          }

          public PreflightCheckResult execute(EnvironmentRequest request) {
            throw new IllegalStateException("token=canary-value-93");
          }
        };

    PreflightResult result =
        new PreflightRunner().run(TestRequests.external("db"), List.of(throwing));

    assertThat(result.status()).isEqualTo(EnvironmentStatus.UNKNOWN);
    assertThat(result.toString())
        .doesNotContain("canary-value-93")
        .contains(DiagnosticSanitizer.REDACTED);
  }

  private static PreflightCheck check(
      String id, Optional<Capability> capability, EnvironmentStatus status) {
    return new PreflightCheck() {
      public String id() {
        return id;
      }

      public PreflightCheckType type() {
        return PreflightCheckType.READINESS;
      }

      public Optional<Capability> capability() {
        return capability;
      }

      public PreflightCheckResult execute(EnvironmentRequest request) {
        return new PreflightCheckResult(
            id, type(), capability, status, "checked", "correct it", Map.of(), Instant.now());
      }
    };
  }
}
