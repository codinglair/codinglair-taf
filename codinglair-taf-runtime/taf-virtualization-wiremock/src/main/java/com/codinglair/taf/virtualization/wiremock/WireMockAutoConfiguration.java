package com.codinglair.taf.virtualization.wiremock;

import com.codinglair.taf.runtime.environment.*;
import com.codinglair.taf.runtime.environment.spring.EnvironmentAutoConfiguration;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(EnvironmentAutoConfiguration.class)
@EnableConfigurationProperties(WireMockProperties.class)
@ConditionalOnProperty(
    prefix = "taf.virtualization.wiremock",
    name = "enabled",
    havingValue = "true")
public class WireMockAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  NetworkFaultPolicy wireMockNetworkFaultPolicy(WireMockProperties properties) {
    return environment ->
        properties.isNetworkFaultsEnabled()
            && !"production".equalsIgnoreCase(environment)
            && !"prod".equalsIgnoreCase(environment);
  }

  @Bean
  @ConditionalOnMissingBean
  WireMockVirtualizationFactory wireMockVirtualizationFactory(NetworkFaultPolicy policy) {
    return new WireMockVirtualizationFactory(policy);
  }

  @Bean
  @ConditionalOnMissingBean
  WireMockEnvironmentProvider wireMockExternalProvider(EnvironmentRegistry registry) {
    var provider = new WireMockEnvironmentProvider();
    registry.register(WireMockEnvironmentProvider.TYPE, EnvironmentMode.EXTERNAL, provider);
    return provider;
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "taf.virtualization.wiremock",
      name = "container-enabled",
      havingValue = "true")
  @ConditionalOnBean(ContainerLifecycleCoordinator.class)
  @ConditionalOnMissingBean
  WireMockContainerEnvironmentProvider wireMockContainerProvider(
      EnvironmentRegistry registry, ContainerLifecycleCoordinator coordinator) {
    var provider = new WireMockContainerEnvironmentProvider(coordinator);
    registry.register(WireMockEnvironmentProvider.TYPE, EnvironmentMode.CONTAINER, provider);
    return provider;
  }
}
