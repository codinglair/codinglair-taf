package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AwsAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AwsAutoConfiguration.class, TafRuntimeAutoConfiguration.class));

  @Test
  void absentByDefault() {
    runner.run(context -> assertThat(context).doesNotHaveBean(AwsProperties.class));
  }

  @Test
  void bindsTwoNamedInstancesOfEachController() {
    runner
        .withPropertyValues(
            "taf.aws.enabled=true",
            "taf.aws.profiles.local.region=us-east-1",
            "taf.aws.profiles.local.endpoint-mode=localstack",
            "taf.aws.profiles.local.endpoint-override=http://localhost:4566",
            "taf.aws.profiles.local.ownership-mode=test-owned",
            "taf.aws.profiles.local.sqs.orders.queue=http://localhost:4566/000/orders",
            "taf.aws.profiles.local.sqs.audit.queue=http://localhost:4566/000/audit",
            "taf.aws.profiles.local.eventbridge.orders.event-bus=orders",
            "taf.aws.profiles.local.eventbridge.audit.event-bus=audit")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              try (var session = context.getBean(TestSessionFactory.class).create()) {
                assertThat(
                        session
                            .getControllerRegistry()
                            .hasController(SqsController.class, "orders"))
                    .isTrue();
                assertThat(
                        session.getControllerRegistry().hasController(SqsController.class, "audit"))
                    .isTrue();
                assertThat(
                        session
                            .getControllerRegistry()
                            .hasController(EventBridgeController.class, "orders"))
                    .isTrue();
                assertThat(
                        session
                            .getControllerRegistry()
                            .hasController(EventBridgeController.class, "audit"))
                    .isTrue();
              }
            });
  }

  @Test
  void missingRegionFailsWithActionableSanitizedError() {
    runner
        .withPropertyValues("taf.aws.enabled=true", "taf.aws.profiles.local.endpoint-mode=aws")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining("taf.aws.profiles.local.region");
            });
  }
}
