package com.codinglair.taf.runtime.environment.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import com.codinglair.taf.runtime.environment.PreflightCheckResult;
import com.codinglair.taf.runtime.environment.PreflightCheckType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ConsumerPreflightTest {
  @Test
  void aggregatesUnresolvedSecretsSelectionsAndDependencyReadinessWithoutSecretValues() {
    var properties = new ConsumerConfigurationProperties();
    properties.setEnvironment("ci");
    var web = capability(properties, "storefront", true);
    web.getRequiredValues().put("base-url", "REPLACE_ME");
    web.getSecretReferences().put("password", "STOREFRONT_PASSWORD");
    web.getRequires().add("database");
    web.getIncompatibleWith().add("legacy-web");
    capability(properties, "legacy-web", true).setType("legacy");
    var disabled = capability(properties, "unused", false);
    disabled.getRequiredValues().put("endpoint", "REPLACE_ME");

    ConsumerPreflightContributor dependency =
        contributor(
            "test",
            (name, instance, configuration) ->
                List.of(
                    new PreflightCheckResult(
                        "dependency." + name,
                        PreflightCheckType.READINESS,
                        Optional.empty(),
                        EnvironmentStatus.UNAVAILABLE,
                        "Required endpoint is unreachable",
                        "Start the controlled dependency",
                        Map.of(),
                        Instant.now())));
    var preflight = preflight(properties, reference -> false, List.of(dependency));

    assertThatThrownBy(preflight::verify)
        .isInstanceOfSatisfying(
            ConsumerPreflightException.class,
            failure -> {
              assertThat(failure.result().checks()).hasSize(5);
              assertThat(failure.result().status()).isEqualTo(EnvironmentStatus.MISCONFIGURED);
              assertThat(failure.getMessage())
                  .contains("storefront", "base-url", "ci", "dependency.storefront")
                  .doesNotContain("STOREFRONT_PASSWORD");
              assertThat(failure.result().checks())
                  .noneMatch(check -> check.checkId().contains("unused"));
            });
  }

  @Test
  void safeDefaultsAndAvailableReferencesPassAndConcurrentInspectionDoesNotMutateConfiguration()
      throws Exception {
    var properties = new ConsumerConfigurationProperties();
    var api = capability(properties, "catalog", true);
    api.getRequiredValues().put("timeout", "30s");
    api.getSecretReferences().put("token", "CATALOG_TOKEN");
    var readyContributor =
        contributor(
            "test",
            (name, instance, configuration) ->
                List.of(
                    new PreflightCheckResult(
                        "dependency.ready",
                        PreflightCheckType.READINESS,
                        Optional.empty(),
                        EnvironmentStatus.READY,
                        "Dependency is ready",
                        "",
                        Map.of(),
                        Instant.now()),
                    new PreflightCheckResult(
                        "dependency.degraded",
                        PreflightCheckType.READINESS,
                        Optional.empty(),
                        EnvironmentStatus.DEGRADED,
                        "Optional feature is degraded",
                        "Continue with warning",
                        Map.of(),
                        Instant.now())));
    var preflight = preflight(properties, "CATALOG_TOKEN"::equals, List.of(readyContributor));

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          java.util.stream.IntStream.range(0, 32)
              .mapToObj(
                  index ->
                      (java.util.concurrent.Callable<Integer>)
                          () -> preflight.inspect().checks().size())
              .toList();
      assertThat(executor.invokeAll(tasks)).allSatisfy(future -> assertThat(future.get()).isZero());
    }
    preflight.verify();
    assertThat(properties.getCapabilities()).containsOnlyKeys("catalog");
  }

  @Test
  void contributorFailureBecomesSanitizedAggregatedDiagnostic() {
    var properties = new ConsumerConfigurationProperties();
    capability(properties, "selected", true);
    var preflight =
        preflight(
            properties,
            ignored -> true,
            List.of(
                contributor(
                    "test",
                    (name, instance, configuration) -> {
                      throw new IllegalStateException("credential=literal-secret");
                    })));
    assertThatThrownBy(preflight::verify)
        .isInstanceOfSatisfying(
            ConsumerPreflightException.class,
            failure ->
                assertThat(failure.getMessage())
                    .contains("Dependency readiness check failed")
                    .doesNotContain("literal-secret"));
  }

  private static ConsumerPreflightContributor contributor(String type, ContributorCheck check) {
    return new ConsumerPreflightContributor() {
      @Override
      public String capabilityType() {
        return type;
      }

      @Override
      public List<PreflightCheckResult> check(
          String name,
          ConsumerConfigurationProperties.CapabilityInstance instance,
          ConsumerConfigurationProperties configuration) {
        return check.apply(name, instance, configuration);
      }
    };
  }

  private static ConsumerPreflight preflight(
      ConsumerConfigurationProperties properties,
      SecretReferenceAvailability secrets,
      List<ConsumerPreflightContributor> contributors) {
    var core =
        new com.codinglair.taf.runtime.core.preflight.ConsumerPreflight(
            List.of(
                new EnvironmentConsumerPreflightContributor(properties, secrets, contributors)));
    return new ConsumerPreflight(core);
  }

  @FunctionalInterface
  private interface ContributorCheck {
    List<PreflightCheckResult> apply(
        String name,
        ConsumerConfigurationProperties.CapabilityInstance instance,
        ConsumerConfigurationProperties configuration);
  }

  private static ConsumerConfigurationProperties.CapabilityInstance capability(
      ConsumerConfigurationProperties properties, String name, boolean enabled) {
    var instance = new ConsumerConfigurationProperties.CapabilityInstance();
    instance.setEnabled(enabled);
    instance.setType("test");
    properties.getCapabilities().put(name, instance);
    return instance;
  }
}
