package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.messaging.aws.environment.AwsServiceEnvironmentContributor;
import com.codinglair.taf.messaging.aws.environment.AwsServiceEnvironmentContributors;
import com.codinglair.taf.messaging.aws.environment.LocalStackEnvironmentProvider;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.environment.EnvironmentMode;
import com.codinglair.taf.runtime.environment.EnvironmentRegistry;
import com.codinglair.taf.runtime.environment.spring.EnvironmentAutoConfiguration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import software.amazon.awssdk.services.sqs.SqsClient;

@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 1000)
@AutoConfigureAfter({TafRuntimeAutoConfiguration.class, EnvironmentAutoConfiguration.class})
@ConditionalOnClass(SqsClient.class)
@ConditionalOnProperty(prefix = "taf.aws", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AwsProperties.class)
public class AwsAutoConfiguration {
  @Bean
  AwsServiceEnvironmentContributors awsServiceEnvironmentContributors(
      List<AwsServiceEnvironmentContributor> contributors) {
    return new AwsServiceEnvironmentContributors(contributors);
  }

  @Bean
  @ConditionalOnBean(EnvironmentRegistry.class)
  LocalStackEnvironmentProvider managedLocalStackEnvironmentProvider(
      AwsProperties properties, EnvironmentRegistry registry) {
    var provider = new LocalStackEnvironmentProvider(EnvironmentMode.CONTAINER, properties);
    registry.register(LocalStackEnvironmentProvider.TYPE, EnvironmentMode.CONTAINER, provider);
    return provider;
  }

  @Bean
  @ConditionalOnBean(EnvironmentRegistry.class)
  LocalStackEnvironmentProvider externalLocalStackEnvironmentProvider(
      AwsProperties properties, EnvironmentRegistry registry) {
    var provider = new LocalStackEnvironmentProvider(EnvironmentMode.EXTERNAL, properties);
    registry.register(LocalStackEnvironmentProvider.TYPE, EnvironmentMode.EXTERNAL, provider);
    return provider;
  }

  @Bean
  TestSessionConfigurer awsSessionConfigurer(AwsProperties properties) {
    validate(properties);
    return session ->
        properties
            .getProfiles()
            .forEach(
                (profileName, profile) -> {
                  profile
                      .getSqs()
                      .forEach(
                          (name, settings) ->
                              session
                                  .getControllerRegistry()
                                  .register(
                                      SqsController.class,
                                      name,
                                      DefaultAwsControllers.sqs(name, profile, settings)));
                  profile
                      .getEventbridge()
                      .forEach(
                          (name, settings) ->
                              session
                                  .getControllerRegistry()
                                  .register(
                                      EventBridgeController.class,
                                      name,
                                      DefaultAwsControllers.eventbridge(name, profile, settings)));
                });
  }

  static void validate(AwsProperties properties) {
    properties.validate();
    Set<String> sqs = new HashSet<>();
    Set<String> eventbridge = new HashSet<>();
    properties
        .getProfiles()
        .forEach(
            (name, profile) -> {
              profile.validate("taf.aws.profiles." + name);
              profile.getSqs().keySet().forEach(controller -> unique(sqs, controller, "SQS"));
              profile
                  .getEventbridge()
                  .keySet()
                  .forEach(controller -> unique(eventbridge, controller, "EventBridge"));
            });
  }

  private static void unique(Set<String> names, String name, String type) {
    if (!names.add(name))
      AwsOperationPolicy.fail(
          "taf.aws.profiles", "duplicate " + type + " controller name '" + name + "'");
  }
}
