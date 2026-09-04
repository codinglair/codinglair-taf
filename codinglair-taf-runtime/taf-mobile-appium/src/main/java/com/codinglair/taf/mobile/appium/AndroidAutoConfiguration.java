package com.codinglair.taf.mobile.appium;

import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionConfigurer;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflightContributor;
import com.codinglair.taf.runtime.core.preflight.PreflightDiagnostic;
import io.appium.java_client.android.AndroidDriver;
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
@ConditionalOnClass(AndroidDriver.class)
@ConditionalOnProperty(prefix = "taf.mobile.android", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AndroidProperties.class)
public class AndroidAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  AndroidControllerFactory androidControllerFactory(AndroidProperties properties) {
    return name ->
        new DefaultAndroidController(
            name, properties.settings(name), AppiumAndroidSessionFactory.standard());
  }

  @Bean
  TestSessionConfigurer androidSessionConfigurer(
      AndroidProperties properties, AndroidControllerFactory factory) {
    return session -> {
      if (properties.getControllers().isEmpty())
        session
            .getControllerRegistry()
            .register(AndroidController.class, "default", factory.create("default"));
      properties
          .getControllers()
          .keySet()
          .forEach(
              name ->
                  session
                      .getControllerRegistry()
                      .register(AndroidController.class, name, factory.create(name)));
    };
  }

  @Bean
  ConsumerPreflightContributor androidPreflightContributor(AndroidProperties properties) {
    return () -> {
      var diagnostics = new ArrayList<PreflightDiagnostic>();
      if (properties.getControllers().isEmpty()) inspect("default", properties, diagnostics);
      properties
          .getControllers()
          .keySet()
          .forEach(name -> inspect(name, properties.settings(name), diagnostics));
      return List.copyOf(diagnostics);
    };
  }

  private static void inspect(
      String name, AndroidControllerSettings settings, List<PreflightDiagnostic> diagnostics) {
    try {
      settings.validate("taf.mobile.android.controllers." + name);
    } catch (IllegalArgumentException failure) {
      diagnostics.add(
          new PreflightDiagnostic(
              "mobile-android." + name,
              "Android Appium configuration is invalid",
              "Correct taf.mobile.android.controllers." + name));
    }
  }
}
