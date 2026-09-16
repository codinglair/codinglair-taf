package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.common.AwsAutoConfiguration;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.messaging.jms.JmsAutoConfiguration;
import com.codinglair.taf.messaging.jms.JmsConnectionFactoryProvider;
import com.codinglair.taf.messaging.jms.JmsController;
import com.codinglair.taf.messaging.kafka.KafkaAutoConfiguration;
import com.codinglair.taf.messaging.kafka.KafkaController;
import com.codinglair.taf.messaging.rabbitmq.RabbitAutoConfiguration;
import com.codinglair.taf.messaging.rabbitmq.RabbitController;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Messaging starter contexts")
class MessagingStarterContextTest {
  private final ApplicationContextRunner maximalRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TafRuntimeAutoConfiguration.class,
                  KafkaAutoConfiguration.class,
                  RabbitAutoConfiguration.class,
                  JmsAutoConfiguration.class,
                  AwsAutoConfiguration.class));

  @Nested
  @DisplayName("Fail-closed activation")
  class FailClosedActivation {
    @Test
    @DisplayName("all provider starters remain inert without explicit enablement")
    void providerStartersRemainInert() {
      maximalRunner.run(
          context -> {
            assertThat(context).hasSingleBean(TestSessionFactory.class);
            try (var session = context.getBean(TestSessionFactory.class).create()) {
              var controllers = session.getControllerRegistry();
              assertThat(controllers.hasController(KafkaController.class, "default")).isFalse();
              assertThat(controllers.hasController(RabbitController.class, "default")).isFalse();
              assertThat(controllers.hasController(JmsController.class, "default")).isFalse();
              assertThat(controllers.hasController(SqsController.class, "default")).isFalse();
              assertThat(controllers.hasController(EventBridgeController.class, "default"))
                  .isFalse();
            }
          });
    }
  }

  @Nested
  @DisplayName("Maximal provider composition")
  class MaximalProviderComposition {
    @Test
    @DisplayName("all configured providers share one session without bean ambiguity")
    void allProvidersCompose() {
      maximalRunner
          .withBean(JmsConnectionFactoryProvider.class, () -> (name, settings, context) -> null)
          .withPropertyValues(
              "taf.messaging.kafka.enabled=true",
              "taf.messaging.kafka.controllers.kafka.bootstrap-servers[0]=localhost:9092",
              "taf.messaging.rabbitmq.enabled=true",
              "taf.messaging.rabbitmq.controllers.rabbit.addresses=localhost:5672",
              "taf.messaging.jms.enabled=true",
              "taf.messaging.jms.controllers.jms.client-id=test",
              "taf.aws.enabled=true",
              "taf.aws.profiles.local.region=us-east-1",
              "taf.aws.profiles.local.endpoint-mode=localstack",
              "taf.aws.profiles.local.endpoint-override=http://localhost:4566",
              "taf.aws.profiles.local.ownership-mode=test-owned",
              "taf.aws.profiles.local.sqs.sqs.queue=http://localhost:4566/000/sqs",
              "taf.aws.profiles.local.eventbridge.events.event-bus=events")
          .run(
              context -> {
                assertThat(context).hasNotFailed().hasSingleBean(TestSessionFactory.class);
                try (var session = context.getBean(TestSessionFactory.class).create()) {
                  var controllers = session.getControllerRegistry();
                  assertThat(controllers.hasController(KafkaController.class, "kafka")).isTrue();
                  assertThat(controllers.hasController(RabbitController.class, "rabbit")).isTrue();
                  assertThat(controllers.hasController(JmsController.class, "jms")).isTrue();
                  assertThat(controllers.hasController(SqsController.class, "sqs")).isTrue();
                  assertThat(controllers.hasController(EventBridgeController.class, "events"))
                      .isTrue();
                }
              });
    }
  }
}
