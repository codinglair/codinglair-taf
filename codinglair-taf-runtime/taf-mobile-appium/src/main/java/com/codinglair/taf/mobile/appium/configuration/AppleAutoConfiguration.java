package com.codinglair.taf.mobile.appium.configuration;

import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.mobile.appium.service.AppleControllerFactory;
import com.codinglair.taf.mobile.appium.service.DefaultAppleController;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import io.appium.java_client.ios.IOSDriver;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(TafRuntimeAutoConfiguration.class)
@ConditionalOnClass(IOSDriver.class)
@ConditionalOnProperty(prefix = "taf.mobile.apple", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AppleProperties.class)
public class AppleAutoConfiguration {
  private static List<String> names(AppleProperties properties) {
    return properties.getControllers().isEmpty()
        ? List.of("default")
        : List.copyOf(properties.getControllers().keySet());
  }

  @Bean
  @ConditionalOnMissingBean
  AppleControllerFactory appleControllerFactory(AppleProperties properties) {
    return name ->
        new DefaultAppleController(name, properties, properties.getControllers().get(name));
  }

  @Bean
  TestSessionConfigurer appleSessionConfigurer(
      AppleProperties properties, AppleControllerFactory factory) {
    return session ->
        names(properties)
            .forEach(
                name ->
                    session
                        .getControllerRegistry()
                        .register(AppleController.class, name, factory.create(name)));
  }

  @Bean
  ConsumerPreflightContributor applePreflightContributor(AppleProperties properties) {
    return () ->
        names(properties).stream()
            .flatMap(
                name -> {
                  try {
                    properties.settings(name);
                    return Stream.<PreflightDiagnostic>empty();
                  } catch (IllegalArgumentException failure) {
                    return Stream.of(
                        new PreflightDiagnostic(
                            "mobile-apple." + name,
                            "Apple Appium configuration is invalid",
                            "Correct taf.mobile.apple.controllers." + name));
                  }
                })
            .toList();
  }
}
