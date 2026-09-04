package com.codinglair.taf.api.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("SOAP auto-configuration")
class SoapAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class, SoapAutoConfiguration.class));

  @Test
  @DisplayName("stays absent when disabled")
  void disabled() {
    runner.run(context -> assertThat(context).doesNotHaveBean(SoapControllerFactory.class));
  }

  @Test
  @DisplayName("creates capability beans when enabled and valid")
  void enabled() {
    runner
        .withPropertyValues(
            "taf.api.soap.enabled=true", "taf.api.soap.endpoint=http://localhost:8080/service")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SoapControllerFactory.class);
              assertThat(context).getBeans(TestSessionConfigurer.class).isNotEmpty();
            });
  }

  @Test
  @DisplayName("preflight reports invalid configuration without breaking startup")
  void invalidConfiguration() {
    runner
        .withPropertyValues("taf.api.soap.enabled=true")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
