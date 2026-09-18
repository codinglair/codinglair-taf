package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.kafka.KafkaController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External Kafka starter")
class KafkaStarterConformanceTest {
  @Test
  @DisplayName("starts inert and registers a selected lazy controller")
  void startsInertAndRegistersSelectedController() {
    var runner = new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local");
    runner.run(context -> assertAbsent(context.getBean(TestSessionFactory.class), KafkaController.class));
    runner.withPropertyValues("taf.messaging.kafka.enabled=true",
                  "taf.messaging.kafka.controllers.events.bootstrap-servers[0]=localhost:9092")
        .run(context -> assertPresent(context.getBean(TestSessionFactory.class), KafkaController.class, "events"));
  }

  private static void assertAbsent(TestSessionFactory factory, Class<KafkaController> type) { 
    try (var session = factory.create()) { 
      assertThat(session.getControllerRegistry().hasController(type, "default")).isFalse(); 
    } 
  }

  private static void assertPresent(TestSessionFactory factory, Class<KafkaController> type, String name) { 
    try (var session = factory.create()) { 
      assertThat(session.getControllerRegistry().hasController(type, name)).isTrue(); 
    } 
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
