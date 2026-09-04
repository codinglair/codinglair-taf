package com.codinglair.taf.api.soap;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
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
@ConditionalOnClass(SoapController.class)
@ConditionalOnProperty(prefix = "taf.api.soap", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SoapProperties.class)
public class SoapAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  SoapControllerFactory soapControllerFactory(SoapProperties properties) {
    return name -> new DefaultSoapController(name, properties.settings(name));
  }

  @Bean
  TestSessionConfigurer soapSessionConfigurer(
      SoapProperties properties, SoapControllerFactory factory) {
    return session -> {
      if (properties.getControllers().isEmpty())
        session
            .getControllerRegistry()
            .register(SoapController.class, "default", factory.create("default"));
      properties
          .getControllers()
          .keySet()
          .forEach(
              name ->
                  session
                      .getControllerRegistry()
                      .register(SoapController.class, name, factory.create(name)));
    };
  }

  @Bean
  ConsumerPreflightContributor soapPreflightContributor(SoapProperties properties) {
    return () -> {
      List<PreflightDiagnostic> diagnostics = new ArrayList<>();
      if (properties.getControllers().isEmpty()) inspect("default", properties, diagnostics);
      properties.getControllers().forEach((name, settings) -> inspect(name, settings, diagnostics));
      return List.copyOf(diagnostics);
    };
  }

  private static void inspect(
      String name, SoapControllerSettings settings, List<PreflightDiagnostic> diagnostics) {
    try {
      settings.validate("taf.api.soap.controllers." + name);
    } catch (IllegalArgumentException failure) {
      diagnostics.add(
          new PreflightDiagnostic(
              "api-soap." + name,
              "SOAP configuration is invalid",
              "Correct taf.api.soap.controllers." + name));
    }
  }
}
