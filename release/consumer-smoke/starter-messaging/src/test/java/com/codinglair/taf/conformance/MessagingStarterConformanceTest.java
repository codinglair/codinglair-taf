package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.MessagingController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External generic Messaging starter")
class MessagingStarterConformanceTest {
  @Test
  @DisplayName("starts without installing an operational provider")
  void startsWithoutOperationalProvider() {
    new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local")
        .run(context -> { 
          assertThat(context).hasNotFailed().hasSingleBean(TestSessionFactory.class); 
          try (var session = context.getBean(TestSessionFactory.class).create()) { 
            assertThat(session.getControllerRegistry()
                              .hasController(MessagingController.class, "default"))
                              .isFalse(); 
          } 
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
