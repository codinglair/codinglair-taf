package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.*;
import java.util.Objects;
import java.util.function.Function;

/** Spring boundary factory for plain-Java Testcontainers providers. */
public final class TestcontainersEnvironmentProviderFactory {
  private final ContainerLifecycleCoordinator coordinator;

  public TestcontainersEnvironmentProviderFactory(ContainerLifecycleCoordinator coordinator) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
  }

  public EnvironmentProvider create(
      String id,
      EnvironmentType type,
      ContainerDefinition definition,
      ContainerImagePolicy policy) {
    return create(id, type, definition, policy, ignored -> null);
  }

  public EnvironmentProvider create(
      String id,
      EnvironmentType type,
      ContainerDefinition definition,
      ContainerImagePolicy policy,
      Function<ContainerDefinition, org.testcontainers.containers.GenericContainer<?>> customizer) {
    Objects.requireNonNull(id, "id");
    return new AbstractTestcontainersEnvironmentProvider(type, definition, policy, coordinator) {
      @Override
      public String id() {
        return id;
      }

      @Override
      protected org.testcontainers.containers.GenericContainer<?> newContainer(
          ContainerDefinition value) {
        var customized = customizer.apply(value);
        return customized == null ? super.newContainer(value) : customized;
      }
    };
  }
}
