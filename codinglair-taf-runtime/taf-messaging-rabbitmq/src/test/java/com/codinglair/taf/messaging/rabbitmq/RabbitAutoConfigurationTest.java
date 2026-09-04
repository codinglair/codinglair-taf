package com.codinglair.taf.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("RabbitMQ auto-configuration")
class RabbitAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RabbitAutoConfiguration.class, TafRuntimeAutoConfiguration.class));

  @Nested
  @DisplayName("Conditions and registration")
  class Conditions {
    @Test
    @DisplayName("is disabled by default")
    void disabledByDefault() {
      runner.run(context -> assertThat(context).doesNotHaveBean(RabbitControllerFactory.class));
    }

    @Test
    @DisplayName("reports invalid addresses through preflight without exposing credentials")
    void invalidConfiguration() {
      runner
          .withPropertyValues(
              "taf.messaging.rabbitmq.enabled=true", "taf.messaging.rabbitmq.addresses=")
          .run(
              context ->
                  assertThat(
                          context
                              .getBean(
                                  com.codinglair.taf.runtime.core.preflight.ConsumerPreflight.class)
                              .inspect()
                              .diagnostics())
                      .anyMatch(value -> value.checkId().equals("messaging-rabbitmq.default")));
    }

    @Test
    @DisplayName("registers isolated lazy named controllers")
    void namedControllers() {
      runner
          .withPropertyValues(
              "taf.messaging.rabbitmq.enabled=true",
              "taf.messaging.rabbitmq.controllers.events.addresses=localhost:5672")
          .run(
              context -> {
                TestSessionFactory factory = context.getBean(TestSessionFactory.class);
                try (var first = factory.create();
                    var second = factory.create()) {
                  assertThat(
                          first
                              .getControllerRegistry()
                              .hasController(RabbitController.class, "events"))
                      .isTrue();
                  assertThat(
                          second
                              .getControllerRegistry()
                              .hasController(RabbitController.class, "events"))
                      .isTrue();
                }
              });
    }
  }
}
