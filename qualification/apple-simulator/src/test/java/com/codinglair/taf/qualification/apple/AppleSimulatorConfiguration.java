package com.codinglair.taf.qualification.apple;

import java.io.IOException;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class AppleSimulatorConfiguration {
  public static final class Initializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
      try {
        var resource = new ClassPathResource("application.yml");
        for (var source : new YamlPropertySourceLoader().load("apple-simulator", resource))
          context.getEnvironment().getPropertySources().addLast(source);
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot load Apple simulator configuration", failure);
      }
    }
  }
}
