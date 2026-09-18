package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.web.playwright.PlaywrightController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External Web starter")
class WebStarterConformanceTest {
  @Test
  @DisplayName("starts and registers a selected lazy controller")
  void startsAndRegistersSelectedController() {
    new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local")
        .withPropertyValues("taf.web.playwright.enabled=true", 
                    "taf.web.playwright.controllers.browser.base-url=https://example.test")
        .run(context -> { 
          assertThat(context).hasNotFailed().hasSingleBean(TestSessionFactory.class); 
          try (var session = context.getBean(TestSessionFactory.class).create()) { 
            assertThat(session.getControllerRegistry().hasController(PlaywrightController.class, "browser")).isTrue(); 
          } 
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
