package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.EnvironmentResource;
import java.util.HashMap;
import java.util.Objects;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Publishes ready resource properties at the Spring composition boundary. */
public final class EnvironmentPropertyPublisher {
  private final ConfigurableEnvironment environment;

  public EnvironmentPropertyPublisher(ConfigurableEnvironment environment) {
    this.environment = Objects.requireNonNull(environment);
  }

  public void publish(EnvironmentResource resource) {
    if (resource.diagnose().status()
        != com.codinglair.taf.runtime.environment.EnvironmentStatus.READY) {
      throw new IllegalStateException(
          "Dynamic properties cannot be published before the resource is ready");
    }
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "taf-environment-" + resource.id(), new HashMap<>(resource.properties())));
  }
}
