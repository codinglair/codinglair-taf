package com.codinglair.taf.runtime.secret.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.secret.EnvironmentValueSource;
import com.codinglair.taf.runtime.secret.ResolvedSecret;
import com.codinglair.taf.runtime.secret.SecretManager;
import com.codinglair.taf.runtime.secret.SecretProvider;
import com.codinglair.taf.runtime.secret.SecretReference;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Secret auto-configuration")
class SecretAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecretAutoConfiguration.class));

  @Nested
  @DisplayName("Explicit selection")
  class ExplicitSelection {
    @Test
    @DisplayName("activates only the selected environment provider")
    void activatesOnlySelectedEnvironmentProvider() {
      runner
          .withPropertyValues("taf.secrets.provider=env")
          .run(
              context -> {
                assertThat(context).hasSingleBean(SecretManager.class);
                assertThat(context).hasSingleBean(ConsumerPreflightContributor.class);
                assertThat(context)
                    .hasBean("environmentSecretProvider")
                    .doesNotHaveBean("jasyptSecretProvider");
              });
    }

    @Test
    @DisplayName("activates only the selected Jasypt provider")
    void activatesOnlySelectedJasyptProvider() {
      runner
          .withBean(EnvironmentValueSource.class, () -> name -> "test-only-value")
          .withPropertyValues("taf.secrets.provider=jasypt")
          .run(
              context ->
                  assertThat(context)
                      .doesNotHaveBean("environmentSecretProvider")
                      .hasBean("jasyptSecretProvider"));
    }

    @Test
    @DisplayName("uses explicit bean routing when providers share an id")
    void routesToOneNamedProviderWhenIdsAreDuplicated() {
      runner
          .withBean("primaryVault", SecretProvider.class, () -> provider("vault"))
          .withBean("secondaryVault", SecretProvider.class, () -> provider("vault"))
          .withPropertyValues("taf.secrets.routing.vault=secondaryVault")
          .run(
              context -> {
                assertThat(context).hasSingleBean(SecretManager.class);
                assertThat(context.getBean(ConsumerPreflightContributor.class).inspect()).isEmpty();
              });
    }
  }

  @Nested
  @DisplayName("Fail-closed startup")
  class FailClosedStartup {
    @Test
    @DisplayName("rejects missing selection in a production-like profile")
    void rejectsMissingSelectionInProductionLikeProfile() {
      runner
          .withSystemProperties("spring.profiles.active=production")
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .hasRootCauseMessage(
                          "capability=secrets; field/profile=taf.secrets.provider; No secret provider is selected for the active profile; corrective-action=Set taf.secrets.provider, configure taf.secrets.routing, or activate the documented taf-local profile"));
    }

    @Test
    @DisplayName("rejects simultaneous default selection and routing")
    void rejectsAmbiguousSelectionAndRouting() {
      runner
          .withPropertyValues(
              "taf.secrets.provider=env", "taf.secrets.routing.env=environmentSecretProvider")
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .hasRootCauseMessage(
                          "capability=secrets; field/profile=taf.secrets.provider/taf.secrets.routing; Secret provider selection is ambiguous for the active profile; corrective-action=Configure either one provider or explicit routing, not both"));
    }

    @Test
    @DisplayName("does not expose an invalid selector in startup diagnostics")
    void keepsStartupDiagnosticsFreeOfConfiguredCanaries() {
      String canary = "credential-canary-" + UUID.randomUUID();
      runner
          .withPropertyValues("taf.secrets.provider=" + canary)
          .run(
              context -> {
                assertThat(context.getStartupFailure()).isNotNull();
                assertThat(context.getStartupFailure().toString()).doesNotContain(canary);
              });
    }
  }

  @Nested
  @DisplayName("Documented local profile")
  class DocumentedLocalProfile {
    @Test
    @DisplayName("explicitly activates only the environment provider")
    void explicitlyActivatesOnlyEnvironmentProvider() {
      runner
          .withSystemProperties("spring.profiles.active=taf-local")
          .run(
              context ->
                  assertThat(context)
                      .hasBean("environmentSecretProvider")
                      .doesNotHaveBean("jasyptSecretProvider"));
    }

    @Test
    @DisplayName("does not override an explicit provider selection")
    void doesNotOverrideExplicitProviderSelection() {
      runner
          .withSystemProperties("spring.profiles.active=taf-local")
          .withBean(EnvironmentValueSource.class, () -> name -> "test-only-value")
          .withPropertyValues("taf.secrets.provider=jasypt")
          .run(
              context ->
                  assertThat(context)
                      .doesNotHaveBean("environmentSecretProvider")
                      .hasBean("jasyptSecretProvider"));
    }
  }

  @Test
  @DisplayName("allows consumers to replace boundary collaborators")
  void permitsReplacementOfBoundaryCollaborators() {
    SecretManager replacement =
        new SecretManager() {
          public com.codinglair.taf.runtime.secret.ResolvedSecret resolve(
              String reference, com.codinglair.taf.runtime.secret.SecretRequestContext context) {
            throw new UnsupportedOperationException();
          }

          public void verifyReady(String reference) {}
        };
    runner
        .withBean(SecretManager.class, () -> replacement)
        .withPropertyValues("taf.secrets.provider=env")
        .run(context -> assertThat(context.getBean(SecretManager.class)).isSameAs(replacement));
  }

  @Test
  @DisplayName("validates selected-provider metadata without decrypting secrets")
  void preflightValidatesMetadataAndReadinessWithoutDecrypting() {
    runner
        .withBean(EnvironmentValueSource.class, () -> name -> "bootstrap-only")
        .withPropertyValues(
            "taf.secrets.routing.env=environmentSecretProvider",
            "taf.secrets.routing.jasypt=jasyptSecretProvider",
            "taf.secrets.references[0]=secret://env/USER_PASSWORD",
            "taf.secrets.references[1]=secret://jasypt/ABCDEFGHIJKLMNOP")
        .run(
            context -> {
              ConsumerPreflightContributor preflight =
                  context.getBean(ConsumerPreflightContributor.class);
              assertThat(preflight.inspect()).isEmpty();
            });
  }

  @Test
  @DisplayName("keeps placeholder and readiness diagnostics free of sensitive payloads")
  void preflightRejectsPlaceholderMalformedAndMissingBootstrapWithoutSensitiveData() {
    runner
        .withBean(EnvironmentValueSource.class, () -> name -> null)
        .withPropertyValues(
            "taf.secrets.routing.env=environmentSecretProvider",
            "taf.secrets.routing.jasypt=jasyptSecretProvider",
            "taf.secrets.references[0]=REPLACE_ME",
            "taf.secrets.references[1]=secret://jasypt/ABCDEFGHIJKLMNOP",
            "taf.secrets.references[2]=secret://vault/private/path")
        .run(
            context -> {
              String diagnostics =
                  context.getBean(ConsumerPreflightContributor.class).inspect().toString();
              assertThat(diagnostics)
                  .contains("secrets.reference", "secrets.jasypt")
                  .doesNotContain("ABCDEFGHIJKLMNOP", "private/path");
            });
  }

  @Test
  @DisplayName("contributes secret diagnostics to the Runtime preflight aggregator")
  void contributesToRuntimePreflightAggregator() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(TafRuntimeAutoConfiguration.class, SecretAutoConfiguration.class))
        .withBean(EnvironmentValueSource.class, () -> name -> null)
        .withPropertyValues(
            "taf.secrets.provider=jasypt",
            "taf.secrets.references[0]=secret://jasypt/ABCDEFGHIJKLMNOP")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ConsumerPreflight.class);
              assertThat(context.getBean(ConsumerPreflight.class).inspect().diagnostics())
                  .extracting(diagnostic -> diagnostic.checkId())
                  .containsExactly("secrets.jasypt");
            });
  }

  private static SecretProvider provider(String id) {
    return new SecretProvider() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public void verifyReady() {}

      @Override
      public ResolvedSecret resolve(SecretReference reference) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
