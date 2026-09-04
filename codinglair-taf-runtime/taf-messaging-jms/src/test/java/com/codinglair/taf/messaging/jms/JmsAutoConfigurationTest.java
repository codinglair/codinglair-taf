package com.codinglair.taf.messaging.jms;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("JMS auto-configuration")
class JmsAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(JmsAutoConfiguration.class, TafRuntimeAutoConfiguration.class));

  @Nested
  @DisplayName("Conditions and registration")
  class Conditions {
    @Test
    @DisplayName("is disabled by default and without a provider")
    void disabledByDefault() {
      runner.run(context -> assertThat(context).doesNotHaveBean(JmsControllerFactory.class));
    }

    @Test
    @DisplayName("registers isolated lazy named controllers when a provider is supplied")
    void namedControllers() {
      runner
          .withBean(JmsConnectionFactoryProvider.class, () -> (name, settings, context) -> null)
          .withPropertyValues(
              "taf.messaging.jms.enabled=true",
              "taf.messaging.jms.controllers.events.client-id=test")
          .run(
              context -> {
                TestSessionFactory factory = context.getBean(TestSessionFactory.class);
                try (var first = factory.create();
                    var second = factory.create()) {
                  assertThat(
                          first
                              .getControllerRegistry()
                              .hasController(JmsController.class, "events"))
                      .isTrue();
                  assertThat(
                          second
                              .getControllerRegistry()
                              .hasController(JmsController.class, "events"))
                      .isTrue();
                }
              });
    }

    @Test
    @DisplayName("reports invalid bounded evidence configuration through preflight")
    void invalidConfiguration() {
      runner
          .withBean(JmsConnectionFactoryProvider.class, () -> (name, settings, context) -> null)
          .withPropertyValues(
              "taf.messaging.jms.enabled=true", "taf.messaging.jms.maximum-evidence-records=0")
          .run(
              context ->
                  assertThat(context.getBean(ConsumerPreflight.class).inspect().diagnostics())
                      .anyMatch(value -> value.checkId().equals("messaging-jms.default")));
    }
  }
}
