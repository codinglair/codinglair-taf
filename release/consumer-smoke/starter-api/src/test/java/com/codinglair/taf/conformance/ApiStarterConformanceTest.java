package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External API starter")
class ApiStarterConformanceTest {
  @Test
  @DisplayName("starts and registers a selected lazy controller")
  void startsAndRegistersSelectedController() {
    new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local")
        .withPropertyValues("taf.api.rest.enabled=true", "taf.api.rest.controllers.catalog.base-url=https://example.test")
        .run(context -> { 
          assertThat(context)
            .hasNotFailed()
            .hasSingleBean(TestSessionFactory.class); 
            try (var session = context.getBean(TestSessionFactory.class).create()) { 
              assertThat(session
                .getControllerRegistry()
                .hasController(RestController.class, "catalog"))
                .isTrue(); 
              } 
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
