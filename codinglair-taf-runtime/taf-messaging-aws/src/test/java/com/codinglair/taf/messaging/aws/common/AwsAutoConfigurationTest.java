package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.environment.AwsResourceDescriptor;
import com.codinglair.taf.messaging.aws.environment.AwsServiceEnvironmentContributor;
import com.codinglair.taf.messaging.aws.environment.AwsServiceEnvironmentContributors;
import com.codinglair.taf.messaging.aws.environment.LocalStackEnvironmentProvider;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.environment.EnvironmentDiagnostic;
import com.codinglair.taf.runtime.environment.EnvironmentMode;
import com.codinglair.taf.runtime.environment.EnvironmentRegistry;
import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import com.codinglair.taf.runtime.environment.spring.EnvironmentAutoConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("AWS auto-configuration")
class AwsAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AwsAutoConfiguration.class, TafRuntimeAutoConfiguration.class));

  @Test
  @DisplayName("is absent by default")
  void absentByDefault() {
    runner.run(context -> assertThat(context).doesNotHaveBean(AwsProperties.class));
  }

  @Test
  @DisplayName("binds two named instances of each controller")
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
  @DisplayName("reports a missing region with an actionable sanitized path")
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

  @Test
  @DisplayName("applies profile overrides while retaining policy defaults")
  void appliesConfigurationPrecedence() {
    runner
        .withPropertyValues(
            "taf.aws.enabled=true",
            "taf.aws.profiles.local.region=us-east-1",
            "taf.aws.profiles.local.endpoint-mode=localstack",
            "taf.aws.profiles.local.endpoint-override=http://localhost:4566",
            "taf.aws.profiles.local.policy.maximum-evidence-bytes=2048")
        .run(
            context -> {
              AwsOperationPolicy policy =
                  context.getBean(AwsProperties.class).getProfiles().get("local").getPolicy();
              assertThat(policy.getMaximumEvidenceBytes()).isEqualTo(2048);
              assertThat(policy.getRetryAttempts()).isEqualTo(3);
            });
  }

  @Test
  @DisplayName("registers managed and external LocalStack providers when environments are present")
  void registersEnvironmentProviders() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                AwsAutoConfiguration.class,
                EnvironmentAutoConfiguration.class,
                TafRuntimeAutoConfiguration.class))
        .withPropertyValues(
            "taf.aws.enabled=true",
            "taf.aws.profiles.local.region=us-east-1",
            "taf.aws.profiles.local.endpoint-mode=localstack",
            "taf.aws.profiles.local.ownership-mode=test-owned")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              EnvironmentRegistry registry = context.getBean(EnvironmentRegistry.class);
              assertThat(
                      registry.providerFor(
                          LocalStackEnvironmentProvider.TYPE, EnvironmentMode.CONTAINER))
                  .isNotNull();
              assertThat(
                      registry.providerFor(
                          LocalStackEnvironmentProvider.TYPE, EnvironmentMode.EXTERNAL))
                  .isNotNull();
            });
  }

  @Nested
  @DisplayName("Environment contributor discovery")
  class ContributorDiscovery {
    @Test
    @DisplayName("discovers service contributors without coupling controller contracts")
    void discoversContributors() {
      AwsServiceEnvironmentContributor sqs = contributor("sqs");
      AwsServiceEnvironmentContributor eventbridge = contributor("eventbridge");

      runner
          .withPropertyValues(
              "taf.aws.enabled=true",
              "taf.aws.profiles.local.region=us-east-1",
              "taf.aws.profiles.local.endpoint-mode=localstack",
              "taf.aws.profiles.local.endpoint-override=http://localhost:4566")
          .withBean("sqsContributor", AwsServiceEnvironmentContributor.class, () -> sqs)
          .withBean(
              "eventBridgeContributor", AwsServiceEnvironmentContributor.class, () -> eventbridge)
          .run(
              context -> {
                var discovered = context.getBean(AwsServiceEnvironmentContributors.class);
                assertThat(discovered.all()).containsExactlyInAnyOrder(sqs, eventbridge);
                assertThat(discovered.require("sqs")).isSameAs(sqs);
              });
    }

    private AwsServiceEnvironmentContributor contributor(String service) {
      return new AwsServiceEnvironmentContributor() {
        public String service() {
          return service;
        }

        public List<AwsResourceDescriptor> describeResources(String profileName) {
          return List.of();
        }

        public EnvironmentDiagnostic readiness(String profileName) {
          return new EnvironmentDiagnostic(
              EnvironmentStatus.READY, "ready", "", Map.of(), Instant.EPOCH);
        }
      };
    }
  }
}
