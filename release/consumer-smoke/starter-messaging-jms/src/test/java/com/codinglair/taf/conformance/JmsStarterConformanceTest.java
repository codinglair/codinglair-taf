package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.jms.JmsConnectionFactoryProvider;
import com.codinglair.taf.messaging.jms.JmsController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External JMS starter")
class JmsStarterConformanceTest {
  @Test
  @DisplayName("starts inert and registers a selected lazy controller")
  void startsInertAndRegistersSelectedController() {
    var runner = new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local");
    runner.run(context -> { 
      try (var session = context.getBean(TestSessionFactory.class).create()) { 
        assertThat(session.getControllerRegistry().hasController(JmsController.class, "default")).isFalse(); 
      } 
    });
    runner.withBean(JmsConnectionFactoryProvider.class, () -> 
                  (name, settings, context) -> null)
                  .withPropertyValues("taf.messaging.jms.enabled=true", 
                                      "taf.messaging.jms.controllers.events.client-id=test")
        .run(context -> { 
          try (var session = context.getBean(TestSessionFactory.class).create()) { 
            assertThat(session.getControllerRegistry().hasController(JmsController.class, "events")).isTrue(); 
          } 
        });
  }


  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
