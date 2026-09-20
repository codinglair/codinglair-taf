package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.database.DatabaseController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External Database starter")
class DatabaseStarterConformanceTest {
  @Test
  @DisplayName("starts and registers a selected lazy controller")
  void startsAndRegistersSelectedController() {
    new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local")
        .withPropertyValues("taf.database.enabled=true", "taf.database.connections.orders.jdbc-url=jdbc:taf-test://localhost/orders")
        .run(context -> { 
          assertThat(context)
            .hasNotFailed()
            .hasSingleBean(TestSessionFactory.class); 
            try (var session = context.getBean(TestSessionFactory.class).create()) { 
              assertThat(session
                .getControllerRegistry()
                .hasController(DatabaseController.class, "orders"))
                .isTrue(); 
              } 
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
