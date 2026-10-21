package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.api.rest.RestController;
import com.codinglair.taf.database.DatabaseController;
import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeController;
import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.messaging.jms.JmsController;
import com.codinglair.taf.messaging.kafka.KafkaController;
import com.codinglair.taf.messaging.rabbitmq.RabbitController;
import com.codinglair.taf.mobile.appium.AndroidController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.web.playwright.PlaywrightController;
import org.testng.annotations.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
class MaximalStarterConformanceTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(ConsumerConfiguration.class)
          .withPropertyValues("spring.profiles.active=taf-local");

  @Test
  void startsWithInactiveProviders() {
    runner.run(context -> { 
      assertThat(context)
        .hasNotFailed()
        .hasSingleBean(TestSessionFactory.class); 
        try (var session = context.getBean(TestSessionFactory.class).create()) { 
          var controllers = session.getControllerRegistry(); 
          assertThat(controllers.hasController(KafkaController.class, "default")).isFalse(); 
          assertThat(controllers.hasController(RabbitController.class, "default")).isFalse(); 
          assertThat(controllers.hasController(JmsController.class, "default")).isFalse(); 
          assertThat(controllers.hasController(SqsController.class, "default")).isFalse(); 
          assertThat(controllers.hasController(EventBridgeController.class, "default")).isFalse(); 
        } 
      });
  }

  @Test
  void registersTopLevelControllers() {
    runner.withPropertyValues("taf.web.playwright.enabled=true", 
                              "taf.web.playwright.controllers.browser.base-url=https://example.test", 
                              "taf.api.rest.enabled=true", 
                              "taf.api.rest.controllers.catalog.base-url=https://example.test", 
                              "taf.database.enabled=true", 
                              "taf.database.connections.orders.jdbc-url=jdbc:taf-test://localhost/orders", 
                              "taf.mobile.android.enabled=true", 
                              "taf.mobile.android.controllers.device.server-url=http://127.0.0.1:4723", 
                              "taf.mobile.android.controllers.device.device-name=emulator", 
                              "taf.mobile.android.controllers.device.app-package=com.example.app")
        .run(context -> { 
          assertThat(context).hasNotFailed().hasSingleBean(TestSessionFactory.class); 
          try (var session = context.getBean(TestSessionFactory.class).create()) { 
            var controllers = session.getControllerRegistry(); 
            assertThat(controllers.hasController(PlaywrightController.class, "browser")).isTrue(); 
            assertThat(controllers.hasController(RestController.class, "catalog")).isTrue(); 
            assertThat(controllers.hasController(DatabaseController.class, "orders")).isTrue(); 
            assertThat(controllers.hasController(AndroidController.class, "device")).isTrue(); 
          } 
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class ConsumerConfiguration {}
}
