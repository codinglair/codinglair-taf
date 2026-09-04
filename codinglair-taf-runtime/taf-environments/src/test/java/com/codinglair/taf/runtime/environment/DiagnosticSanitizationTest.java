package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiagnosticSanitizationTest {
  @Test
  void diagnosticsNeverExposeSecretCanaries() {
    String canary = "canary-value-93";
    PreflightCheckResult result =
        new PreflightCheckResult(
            "auth",
            PreflightCheckType.AUTHENTICATION,
            Optional.empty(),
            EnvironmentStatus.UNAVAILABLE,
            "authorization=Bearer " + canary,
            "replace token=" + canary,
            Map.of("api-key", canary, "detail", "password=" + canary),
            Instant.now());

    assertThat(result.toString()).doesNotContain(canary).contains(DiagnosticSanitizer.REDACTED);
  }

  @Test
  void failureMessageAndTaxonomyAreSanitized() {
    EnvironmentRequest request = TestRequests.external("db");
    EnvironmentProvisioningException failure =
        new EnvironmentProvisioningException(
            "fake", request, "token=canary-value-93", "replace secret=canary-value-93");

    assertThat(failure.getMessage()).doesNotContain("canary-value-93");
    assertThat(failure.error().type())
        .isEqualTo(com.codinglair.taf.core.Error.ErrorType.ENVIRONMENT_ISSUE);
    assertThat(failure.error().toString()).doesNotContain("canary-value-93");
  }

  @Test
  void providerCauseCannotLeakSecretCanary() {
    EnvironmentProvisioningException failure =
        new EnvironmentProvisioningException(
            "fake",
            TestRequests.external("db"),
            "failed",
            "inspect diagnostics",
            new IllegalStateException("password=canary-value-93"));

    assertThat(failure.getCause().getMessage()).doesNotContain("canary-value-93");
  }
}
