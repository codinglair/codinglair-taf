package com.codinglair.taf.web.playwright;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.SessionAwareAccessor;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import com.microsoft.playwright.Playwright;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(TafRuntimeAutoConfiguration.class)
@ConditionalOnClass(Playwright.class)
@ConditionalOnProperty(prefix = "taf.web.playwright", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PlaywrightProperties.class)
public class PlaywrightAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  PlaywrightControllerFactory playwrightControllerFactory(PlaywrightProperties properties) {
    return name -> new DefaultPlaywrightController(name, properties.settings(name));
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(SessionAwareAccessor.class)
  PlaywrightObjectFactory playwrightObjectFactory(SessionAwareAccessor sessions) {
    return new PlaywrightObjectFactory(sessions);
  }

  @Bean
  TestSessionConfigurer playwrightSessionConfigurer(
      PlaywrightProperties properties, PlaywrightControllerFactory factory) {
    return session -> {
      if (properties.isEnabled() && properties.getControllers().isEmpty()) {
        session
            .getControllerRegistry()
            .register(PlaywrightController.class, "default", factory.create("default"));
      }
      properties
          .getControllers()
          .forEach(
              (name, ignored) ->
                  session
                      .getControllerRegistry()
                      .register(PlaywrightController.class, name, factory.create(name)));
    };
  }

  @Bean
  ConsumerPreflightContributor playwrightPreflightContributor(PlaywrightProperties properties) {
    return new ConsumerPreflightContributor() {
      @Override
      public List<PreflightDiagnostic> inspect() {
        var diagnostics = new java.util.ArrayList<PreflightDiagnostic>();
        if (properties.isEnabled() && properties.getControllers().isEmpty()) {
          inspect("default", properties, diagnostics);
        }
        properties
            .getControllers()
            .keySet()
            .forEach(name -> inspect(name, properties, diagnostics));
        return List.copyOf(diagnostics);
      }

      private void inspect(
          String name, PlaywrightProperties properties, List<PreflightDiagnostic> diagnostics) {
        try {
          properties.settings(name).validate("taf.web.playwright.controllers." + name);
        } catch (IllegalArgumentException failure) {
          diagnostics.add(
              new PreflightDiagnostic(
                  "web-playwright." + name,
                  "Playwright configuration is invalid",
                  "Correct taf.web.playwright.controllers." + name));
        }
      }
    };
  }
}
