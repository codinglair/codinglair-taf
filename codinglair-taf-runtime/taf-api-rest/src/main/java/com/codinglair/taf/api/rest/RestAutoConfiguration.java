package com.codinglair.taf.api.rest;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import io.restassured.RestAssured;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(TafRuntimeAutoConfiguration.class)
@ConditionalOnClass(RestAssured.class)
@ConditionalOnProperty(prefix = "taf.api.rest", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RestProperties.class)
public class RestAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  RestContractValidator restContractValidator() {
    return RestContractValidator.NONE;
  }

  @Bean
  @ConditionalOnMissingBean
  RestControllerFactory restControllerFactory(
      RestProperties properties, RestContractValidator validator) {
    return name -> new DefaultRestController(name, properties.settings(name), validator);
  }

  @Bean
  TestSessionConfigurer restSessionConfigurer(
      RestProperties properties, RestControllerFactory factory) {
    return session -> {
      if (properties.getControllers().isEmpty())
        session
            .getControllerRegistry()
            .register(RestController.class, "default", factory.create("default"));
      properties
          .getControllers()
          .forEach(
              (name, ignored) ->
                  session
                      .getControllerRegistry()
                      .register(RestController.class, name, factory.create(name)));
    };
  }

  @Bean
  ConsumerPreflightContributor restPreflightContributor(RestProperties properties) {
    return () -> {
      List<PreflightDiagnostic> diagnostics = new ArrayList<>();
      if (properties.getControllers().isEmpty()) inspect("default", properties, diagnostics);
      properties
          .getControllers()
          .keySet()
          .forEach(name -> inspect(name, properties.settings(name), diagnostics));
      return List.copyOf(diagnostics);
    };
  }

  private static void inspect(
      String name, RestControllerSettings settings, List<PreflightDiagnostic> diagnostics) {
    try {
      settings.validate("taf.api.rest.controllers." + name);
    } catch (IllegalArgumentException failure) {
      diagnostics.add(
          new PreflightDiagnostic(
              "api-rest." + name,
              "REST configuration is invalid",
              "Correct taf.api.rest.controllers." + name));
    }
  }
}
