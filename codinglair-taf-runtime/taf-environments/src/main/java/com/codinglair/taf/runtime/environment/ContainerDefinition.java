package com.codinglair.taf.runtime.environment;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.DockerImageName;

/** Immutable provider definition for a dynamically addressed container. */
public record ContainerDefinition(
    DockerImageName image,
    List<Integer> exposedPorts,
    Map<String, String> environment,
    List<String> command,
    WaitStrategy readiness,
    Map<String, ContainerProperty> properties,
    int maximumLogCharacters) {

  public ContainerDefinition {
    Objects.requireNonNull(image, "image");
    exposedPorts = List.copyOf(Objects.requireNonNull(exposedPorts, "exposedPorts"));
    if (exposedPorts.isEmpty()) {
      throw new IllegalArgumentException("At least one container port must be exposed");
    }
    for (Integer port : exposedPorts) {
      if (port == null || port < 1 || port > 65_535) {
        throw new IllegalArgumentException("Container ports must be between 1 and 65535");
      }
    }
    environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    command = List.copyOf(Objects.requireNonNull(command, "command"));
    Objects.requireNonNull(readiness, "readiness");
    properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
    if (properties.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
      throw new IllegalArgumentException("Dynamic property names must not be blank");
    }
    if (maximumLogCharacters < 1) {
      throw new IllegalArgumentException("Maximum log characters must be positive");
    }
  }

  @Override
  public String toString() {
    return "ContainerDefinition[image="
        + image
        + ", exposedPorts="
        + exposedPorts
        + ", environmentKeys="
        + environment.keySet()
        + ", commandArguments="
        + command.size()
        + ", readiness="
        + readiness.getClass().getSimpleName()
        + ", propertyNames="
        + properties.keySet()
        + ", maximumLogCharacters="
        + maximumLogCharacters
        + "]";
  }
}
