package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import software.amazon.awssdk.services.sqs.SqsClient;

@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 1000)
@AutoConfigureAfter(TafRuntimeAutoConfiguration.class)
@ConditionalOnClass(SqsClient.class)
@ConditionalOnProperty(prefix = "taf.aws", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AwsProperties.class)
public class AwsAutoConfiguration {
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
    if (properties.getProfiles().isEmpty())
      AwsOperationPolicy.fail("taf.aws.profiles", "at least one profile is required");
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
