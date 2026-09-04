package com.codinglair.taf.virtualization.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.environment.spring.EnvironmentAutoConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("WireMock auto-configuration")
class WireMockAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class,
                  EnvironmentAutoConfiguration.class,
                  WireMockAutoConfiguration.class));

  @Test
  @DisplayName("backs off while disabled")
  void disabled() {
    runner.run(context -> assertThat(context).doesNotHaveBean(WireMockVirtualizationFactory.class));
  }

  @Test
  @DisplayName("registers external composition when enabled")
  void external() {
    runner
        .withPropertyValues("taf.virtualization.wiremock.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(WireMockEnvironmentProvider.class);
              assertThat(context).hasSingleBean(WireMockVirtualizationFactory.class);
              assertThat(context).doesNotHaveBean(WireMockContainerEnvironmentProvider.class);
            });
  }

  @Test
  @DisplayName("registers container provider only with explicit opt in")
  void container() {
    runner
        .withPropertyValues(
            "taf.virtualization.wiremock.enabled=true",
            "taf.virtualization.wiremock.container-enabled=true",
            "taf.environment.testcontainers-enabled=true",
            "taf.environment.mode=container")
        .run(
            context ->
                assertThat(context).hasSingleBean(WireMockContainerEnvironmentProvider.class));
  }

  @Test
  @DisplayName("custom network policy backs off the default")
  void customPolicy() {
    NetworkFaultPolicy custom = ignored -> true;
    runner
        .withBean(NetworkFaultPolicy.class, () -> custom)
        .withPropertyValues("taf.virtualization.wiremock.enabled=true")
        .run(context -> assertThat(context.getBean(NetworkFaultPolicy.class)).isSameAs(custom));
  }
}
