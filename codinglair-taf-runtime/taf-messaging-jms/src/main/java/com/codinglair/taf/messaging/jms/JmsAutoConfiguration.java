package com.codinglair.taf.messaging.jms;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.*;
import jakarta.jms.ConnectionFactory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(TafRuntimeAutoConfiguration.class)
@ConditionalOnClass(ConnectionFactory.class)
@ConditionalOnProperty(prefix = "taf.messaging.jms", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(JmsProperties.class)
public class JmsAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  JmsControllerFactory jmsControllerFactory(
      JmsProperties properties, JmsConnectionFactoryProvider provider) {
    return name -> new DefaultJmsController(name, properties.settings(name), provider);
  }

  @Bean
  TestSessionConfigurer jmsSessionConfigurer(
      JmsProperties properties, JmsControllerFactory factory) {
    return session -> {
      if (properties.getControllers().isEmpty())
        session
            .getControllerRegistry()
            .register(JmsController.class, "default", factory.create("default"));
      properties
          .getControllers()
          .keySet()
          .forEach(
              name ->
                  session
                      .getControllerRegistry()
                      .register(JmsController.class, name, factory.create(name)));
    };
  }

  @Bean
  ConsumerPreflightContributor jmsPreflightContributor(JmsProperties properties) {
    return () -> {
      List<PreflightDiagnostic> diagnostics = new ArrayList<>();
      if (properties.getControllers().isEmpty()) inspect("default", properties, diagnostics);
      properties.getControllers().forEach((name, settings) -> inspect(name, settings, diagnostics));
      return List.copyOf(diagnostics);
    };
  }

  private static void inspect(
      String name, JmsControllerSettings settings, List<PreflightDiagnostic> diagnostics) {
    try {
      settings.validate("taf.messaging.jms.controllers." + name);
    } catch (IllegalArgumentException failure) {
      diagnostics.add(
          new PreflightDiagnostic(
              "messaging-jms." + name,
              "JMS configuration is invalid",
              "Correct taf.messaging.jms.controllers." + name));
    }
  }
}
