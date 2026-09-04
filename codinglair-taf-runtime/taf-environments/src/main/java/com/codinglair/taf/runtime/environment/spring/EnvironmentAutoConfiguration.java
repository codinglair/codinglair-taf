package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.*;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.ConfigurableEnvironment;

@AutoConfiguration
@AutoConfigureAfter(com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration.class)
@ConditionalOnClass(EnvironmentRegistry.class)
@EnableConfigurationProperties({
  TafEnvironmentProperties.class,
  ConsumerConfigurationProperties.class
})
public class EnvironmentAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  SecretReferenceAvailability secretReferenceAvailability(ConfigurableEnvironment environment) {
    return environment::containsProperty;
  }

  @Bean
  @ConditionalOnMissingBean
  com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor
      environmentConsumerPreflightContributor(
          ConsumerConfigurationProperties configuration,
          SecretReferenceAvailability secrets,
          List<ConsumerPreflightContributor> contributors) {
    return new EnvironmentConsumerPreflightContributor(configuration, secrets, contributors);
  }

  @Bean
  @ConditionalOnMissingBean
  ConsumerPreflight environmentConsumerPreflight(
      com.codinglair.taf.runtime.core.preflight.ConsumerPreflight delegate) {
    return new ConsumerPreflight(delegate);
  }

  @Bean
  @ConditionalOnMissingBean
  EnvironmentRegistry environmentRegistry() {
    return new DefaultEnvironmentRegistry();
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "taf.environment",
      name = "testcontainers-enabled",
      havingValue = "true")
  @ConditionalOnProperty(prefix = "taf.environment", name = "mode", havingValue = "container")
  ContainerLifecycleCoordinator containerLifecycleCoordinator() {
    return new ContainerLifecycleCoordinator();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ContainerLifecycleCoordinator.class)
  TestcontainersEnvironmentProviderFactory testcontainersEnvironmentProviderFactory(
      ContainerLifecycleCoordinator coordinator) {
    return new TestcontainersEnvironmentProviderFactory(coordinator);
  }

  @Bean
  @ConditionalOnMissingBean
  SessionEnvironmentManager sessionEnvironmentManager(
      EnvironmentRegistry registry, TafEnvironmentProperties properties) {
    return new SessionEnvironmentManager(registry, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  EnvironmentOperationManager environmentOperationManager(
      EnvironmentRegistry registry, TafEnvironmentProperties properties) {
    return new EnvironmentOperationManager(registry, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  EnvironmentPropertyPublisher environmentPropertyPublisher(ConfigurableEnvironment environment) {
    return new EnvironmentPropertyPublisher(environment);
  }

  @Bean(name = "sharedEnvironmentResource", destroyMethod = "cleanup")
  @ConditionalOnBean(SharedEnvironmentRequest.class)
  @ConditionalOnMissingBean(EnvironmentResource.class)
  @ConditionalOnProperty(
      prefix = "taf.environment",
      name = "testcontainers-enabled",
      havingValue = "true")
  @ConditionalOnProperty(prefix = "taf.environment", name = "mode", havingValue = "container")
  @ConditionalOnProperty(prefix = "taf.environment", name = "startup", havingValue = "eager")
  EnvironmentResource sharedEnvironmentResource(
      SharedEnvironmentRequest configured,
      EnvironmentRegistry registry,
      EnvironmentPropertyPublisher publisher,
      TafEnvironmentProperties properties) {
    return provisionShared(configured, registry, publisher, properties);
  }

  @Bean(name = "sharedEnvironmentResource", destroyMethod = "cleanup")
  @Lazy
  @ConditionalOnBean(SharedEnvironmentRequest.class)
  @ConditionalOnMissingBean(EnvironmentResource.class)
  @ConditionalOnProperty(
      prefix = "taf.environment",
      name = "testcontainers-enabled",
      havingValue = "true")
  @ConditionalOnProperty(prefix = "taf.environment", name = "mode", havingValue = "container")
  @ConditionalOnProperty(
      prefix = "taf.environment",
      name = "startup",
      havingValue = "lazy",
      matchIfMissing = true)
  EnvironmentResource lazySharedEnvironmentResource(
      SharedEnvironmentRequest configured,
      EnvironmentRegistry registry,
      EnvironmentPropertyPublisher publisher,
      TafEnvironmentProperties properties) {
    return provisionShared(configured, registry, publisher, properties);
  }

  private EnvironmentResource provisionShared(
      SharedEnvironmentRequest configured,
      EnvironmentRegistry registry,
      EnvironmentPropertyPublisher publisher,
      TafEnvironmentProperties properties) {
    EnvironmentRequest supplied = configured.request();
    var requestProperties = new java.util.HashMap<>(supplied.properties());
    requestProperties.put(ContainerLifecycle.PROPERTY, properties.getLifecycle().name());
    EnvironmentRequest request =
        new EnvironmentRequest(
            supplied.resourceName(),
            supplied.type(),
            properties.getMode(),
            supplied.capabilities(),
            requestProperties,
            properties.getReadinessTimeout());
    EnvironmentProvider provider = registry.providerFor(request.type(), request.mode());
    EnvironmentResource resource = provider.provision(request);
    try {
      publisher.publish(resource);
      return new SpringManagedResource(provider, resource, properties.getCleanup());
    } catch (RuntimeException failure) {
      try {
        provider.release(resource.id());
      } catch (RuntimeException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  private record SpringManagedResource(
      EnvironmentProvider provider,
      EnvironmentResource delegate,
      EnvironmentCleanupPolicy cleanupPolicy)
      implements EnvironmentResource {
    @Override
    public String id() {
      return delegate.id();
    }

    @Override
    public EnvironmentType type() {
      return delegate.type();
    }

    @Override
    public EnvironmentMode mode() {
      return delegate.mode();
    }

    @Override
    public java.util.Map<String, String> properties() {
      return delegate.properties();
    }

    @Override
    public EnvironmentDiagnostic diagnose() {
      return delegate.diagnose();
    }

    @Override
    public void cleanup() {
      if (cleanupPolicy != EnvironmentCleanupPolicy.NEVER) {
        provider.release(delegate.id());
      }
    }
  }
}
