package com.codinglair.taf.runtime.environment;

import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;

final class TestcontainersManagedContainer implements ManagedContainer {
  private final GenericContainer<?> container;
  private final ContainerDefinition definition;
  private final BoundedContainerLog logs;

  TestcontainersManagedContainer(
      GenericContainer<?> container,
      ContainerDefinition definition,
      java.time.Duration startupTimeout) {
    this.container = container;
    this.definition = definition;
    this.logs = new BoundedContainerLog(definition.maximumLogCharacters());
    container.withExposedPorts(definition.exposedPorts().toArray(Integer[]::new));
    container.withEnv(definition.environment());
    if (!definition.command().isEmpty()) {
      container.withCommand(definition.command().toArray(String[]::new));
    }
    container.waitingFor(definition.readiness());
    container.withStartupTimeout(startupTimeout);
    container.withLogConsumer(logs);
  }

  @Override
  public void start() {
    container.start();
  }

  @Override
  public void stop() {
    container.stop();
  }

  @Override
  public boolean isRunning() {
    return container.isRunning();
  }

  @Override
  public Map<String, String> properties() {
    var resolved = new LinkedHashMap<String, String>();
    definition
        .properties()
        .forEach((name, resolver) -> resolved.put(name, resolver.resolve(container)));
    return Map.copyOf(resolved);
  }

  @Override
  public String logs() {
    return logs.snapshot();
  }
}
