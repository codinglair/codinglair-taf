package com.codinglair.taf.runtime.secret.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.secret.EnvironmentValueSource;
import com.codinglair.taf.runtime.secret.SecretManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Secret auto-configuration")
class SecretAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecretAutoConfiguration.class));

  @Test
  @DisplayName("supplies both local providers and one neutral manager")
  void suppliesBothProvidersAndOneManager() {
    runner
        .withBean(EnvironmentValueSource.class, () -> name -> "test-only-value")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SecretManager.class);
              assertThat(context).hasSingleBean(ConsumerPreflightContributor.class);
              assertThat(context)
                  .hasBean("environmentSecretProvider")
                  .hasBean("jasyptSecretProvider");
            });
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
        .run(context -> assertThat(context.getBean(SecretManager.class)).isSameAs(replacement));
  }

  @Test
  @DisplayName("validates selected-provider metadata without decrypting secrets")
  void preflightValidatesMetadataAndReadinessWithoutDecrypting() {
    runner
        .withBean(EnvironmentValueSource.class, () -> name -> "bootstrap-only")
        .withPropertyValues(
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
        .withPropertyValues("taf.secrets.references[0]=secret://jasypt/ABCDEFGHIJKLMNOP")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ConsumerPreflight.class);
              assertThat(context.getBean(ConsumerPreflight.class).inspect().diagnostics())
                  .extracting(diagnostic -> diagnostic.checkId())
                  .containsExactly("secrets.jasypt");
            });
  }
}
