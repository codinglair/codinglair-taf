package com.codinglair.taf.messaging.rabbitmq;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.codinglair.taf.runtime.environment.*;
import com.codinglair.taf.runtime.environment.spring.EnvironmentAutoConfiguration;
import com.codinglair.taf.runtime.secret.SecretManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter({TafRuntimeAutoConfiguration.class, EnvironmentAutoConfiguration.class})
@ConditionalOnClass(CachingConnectionFactory.class)
@ConditionalOnProperty(prefix = "taf.messaging.rabbitmq", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  RabbitControllerFactory rabbitControllerFactory(
      RabbitProperties properties, ObjectProvider<SecretManager> secretManagers) {
    SecretManager secrets = secretManagers.getIfAvailable(UnavailableSecretManager::new);
    return name -> new DefaultRabbitController(name, properties.settings(name), secrets);
  }

  @Bean
  TestSessionConfigurer rabbitSessionConfigurer(
      RabbitProperties properties, RabbitControllerFactory factory) {
    return session -> {
      if (properties.getControllers().isEmpty())
        session
            .getControllerRegistry()
            .register(RabbitController.class, "default", factory.create("default"));
      properties
          .getControllers()
          .keySet()
          .forEach(
              name ->
                  session
                      .getControllerRegistry()
                      .register(RabbitController.class, name, factory.create(name)));
    };
  }

  @Bean
  ConsumerPreflightContributor rabbitPreflightContributor(RabbitProperties properties) {
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
      prefix = "taf.messaging.rabbitmq.container",
      name = "enabled",
      havingValue = "true")
  RabbitEnvironmentProvider rabbitEnvironmentProvider(
      ContainerLifecycleCoordinator coordinator, EnvironmentRegistry registry) {
    RabbitEnvironmentProvider provider = new RabbitEnvironmentProvider(coordinator);
    registry.register(RabbitEnvironmentProvider.TYPE, EnvironmentMode.CONTAINER, provider);
    return provider;
  }

  private static void inspect(
      String name, RabbitControllerSettings settings, List<PreflightDiagnostic> diagnostics) {
    try {
      settings.validate("taf.messaging.rabbitmq.controllers." + name);
    } catch (IllegalArgumentException failure) {
      diagnostics.add(
          new PreflightDiagnostic(
              "messaging-rabbitmq." + name,
              "RabbitMQ configuration is invalid",
              "Correct taf.messaging.rabbitmq.controllers." + name));
    }
  }

  private static final class UnavailableSecretManager implements SecretManager {
    @Override
    public com.codinglair.taf.runtime.secret.ResolvedSecret resolve(
        String reference, com.codinglair.taf.runtime.secret.SecretRequestContext context) {
      throw new IllegalStateException("No SecretManager is configured for RabbitMQ credentials");
    }

    @Override
    public void verifyReady(String reference) {
      throw new IllegalStateException("No SecretManager is configured for RabbitMQ credentials");
    }
  }
}
