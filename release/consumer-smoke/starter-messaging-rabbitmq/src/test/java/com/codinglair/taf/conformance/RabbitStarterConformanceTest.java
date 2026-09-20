package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.rabbitmq.RabbitController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External RabbitMQ starter")
class RabbitStarterConformanceTest {
  @Test
  @DisplayName("starts inert and registers a selected lazy controller")
  void startsInertAndRegistersSelectedController() {
    var runner = new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local");
    runner.run(context -> { 
      try (var session = context.getBean(TestSessionFactory.class).create()) { 
        assertThat(session.getControllerRegistry().hasController(RabbitController.class, "default")).isFalse(); 
      } 
    });
    runner.withPropertyValues("taf.messaging.rabbitmq.enabled=true", "taf.messaging.rabbitmq.controllers.events.addresses=localhost:5672")
        .run(context -> { 
          try (var session = context.getBean(TestSessionFactory.class).create()) { 
            assertThat(session.getControllerRegistry().hasController(RabbitController.class, "events")).isTrue(); 
          } 
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
