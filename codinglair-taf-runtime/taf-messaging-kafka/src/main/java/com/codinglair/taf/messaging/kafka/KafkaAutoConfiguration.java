package com.codinglair.taf.messaging.kafka;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.codinglair.taf.runtime.environment.*;
import com.codinglair.taf.runtime.environment.spring.EnvironmentAutoConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter({TafRuntimeAutoConfiguration.class, EnvironmentAutoConfiguration.class})
@ConditionalOnClass(KafkaProducer.class)
@ConditionalOnProperty(prefix = "taf.messaging.kafka", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  KafkaControllerFactory kafkaControllerFactory(KafkaProperties properties) {
    return name -> new DefaultKafkaController(name, properties.settings(name));
  }

  @Bean
  TestSessionConfigurer kafkaSessionConfigurer(
      KafkaProperties properties, KafkaControllerFactory factory) {
    return session -> {
      if (properties.getControllers().isEmpty())
        session
            .getControllerRegistry()
            .register(KafkaController.class, "default", factory.create("default"));
      properties
          .getControllers()
          .keySet()
          .forEach(
              name ->
                  session
                      .getControllerRegistry()
                      .register(KafkaController.class, name, factory.create(name)));
    };
  }

  @Bean
  ConsumerPreflightContributor kafkaPreflightContributor(KafkaProperties properties) {
    return () -> {
      List<PreflightDiagnostic> diagnostics = new ArrayList<>();
      if (properties.getControllers().isEmpty()) inspect("default", properties, diagnostics);
      properties.getControllers().forEach((name, settings) -> inspect(name, settings, diagnostics));
      return List.copyOf(diagnostics);
    };
  }

  @Bean(destroyMethod = "cleanup")
  @ConditionalOnBean(ContainerLifecycleCoordinator.class)
  @ConditionalOnProperty(
      prefix = "taf.messaging.kafka.container",
      name = "enabled",
      havingValue = "true")
  KafkaEnvironmentProvider kafkaEnvironmentProvider(
      ContainerLifecycleCoordinator coordinator, EnvironmentRegistry registry) {
    KafkaEnvironmentProvider provider = new KafkaEnvironmentProvider(coordinator);
    registry.register(KafkaEnvironmentProvider.TYPE, EnvironmentMode.CONTAINER, provider);
    return provider;
  }

  private static void inspect(
      String name, KafkaControllerSettings settings, List<PreflightDiagnostic> diagnostics) {
    try {
      settings.validate("taf.messaging.kafka.controllers." + name);
    } catch (IllegalArgumentException failure) {
      diagnostics.add(
          new PreflightDiagnostic(
              "messaging-kafka." + name,
              "Kafka configuration is invalid",
              "Correct taf.messaging.kafka.controllers." + name));
    }
  }
}
