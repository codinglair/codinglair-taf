package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mobile.appium.AndroidController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("External Mobile starter")
class MobileStarterConformanceTest {
  @Test
  @DisplayName("starts and registers a selected lazy controller")
  void startsAndRegistersSelectedController() {
    new ApplicationContextRunner()
        .withUserConfiguration(ConsumerConfiguration.class)
        .withPropertyValues("spring.profiles.active=taf-local")
        .withPropertyValues("taf.mobile.android.enabled=true",
                    "taf.mobile.android.controllers.device.server-url=http://127.0.0.1:4723",
                    "taf.mobile.android.controllers.device.device-name=emulator",
                    "taf.mobile.android.controllers.device.app-package=com.example.app")
        .run(context -> {
          assertThat(context).hasNotFailed().hasSingleBean(TestSessionFactory.class);
          try (var session = context.getBean(TestSessionFactory.class).create()) {
            assertThat(session.getControllerRegistry().hasController(AndroidController.class, "device")).isTrue();
          }
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
