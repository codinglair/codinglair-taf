package com.codinglair.taf.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Kafka auto-configuration")
class KafkaAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  KafkaAutoConfiguration.class, TafRuntimeAutoConfiguration.class));

  @Nested
  @DisplayName("Conditions and registration")
  class Conditions {
    @Test
    @DisplayName("is disabled by default")
    void disabledByDefault() {
      runner.run(context -> assertThat(context).doesNotHaveBean(KafkaControllerFactory.class));
    }

    @Test
    @DisplayName("reports missing bootstrap servers through preflight")
    void invalidConfiguration() {
      runner
          .withPropertyValues("taf.messaging.kafka.enabled=true")
          .run(
              context ->
                  assertThat(
                          context
                              .getBean(
                                  com.codinglair.taf.runtime.core.preflight.ConsumerPreflight.class)
                              .inspect()
                              .diagnostics())
                      .anyMatch(value -> value.checkId().equals("messaging-kafka.default")));
    }

    @Test
    @DisplayName("registers isolated lazy named controllers")
    void namedControllers() {
      runner
          .withPropertyValues(
              "taf.messaging.kafka.enabled=true",
              "taf.messaging.kafka.controllers.events.bootstrap-servers[0]=localhost:9092")
          .run(
              context -> {
                TestSessionFactory factory = context.getBean(TestSessionFactory.class);
                try (var first = factory.create();
                    var second = factory.create()) {
                  assertThat(
                          first
                              .getControllerRegistry()
                              .hasController(KafkaController.class, "events"))
                      .isTrue();
                  assertThat(
                          second
                              .getControllerRegistry()
                              .hasController(KafkaController.class, "events"))
                      .isTrue();
                }
              });
    }
  }
}
