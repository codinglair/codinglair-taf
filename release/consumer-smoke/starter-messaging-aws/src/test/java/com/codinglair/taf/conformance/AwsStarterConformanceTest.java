package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External AWS messaging starter")
class AwsStarterConformanceTest {
  @Test
  @DisplayName("starts inert and registers selected lazy controllers")
  void startsInertAndRegistersSelectedControllers() {
    var runner = new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local");
    runner.run(context -> { 
      try (var session = context.getBean(TestSessionFactory.class).create()) { 
        assertThat(session.getControllerRegistry().hasController(SqsController.class, "default")).isFalse(); 
        assertThat(session.getControllerRegistry().hasController(EventBridgeController.class, "default")).isFalse(); } });
    runner.withPropertyValues("taf.aws.enabled=true", 
                              "taf.aws.profiles.local.region=us-east-1", 
                              "taf.aws.profiles.local.endpoint-mode=localstack", 
                              "taf.aws.profiles.local.endpoint-override=http://localhost:4566", 
                              "taf.aws.profiles.local.ownership-mode=test-owned", 
                              "taf.aws.profiles.local.sqs.queue.queue=http://localhost:4566/000/queue", 
                              "taf.aws.profiles.local.eventbridge.events.event-bus=events")
        .run(context -> { 
          try (var session = context.getBean(TestSessionFactory.class).create()) { 
            assertThat(session.getControllerRegistry().hasController(SqsController.class, "queue")).isTrue(); 
            assertThat(session.getControllerRegistry().hasController(EventBridgeController.class, "events")).isTrue(); 
          } 
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
