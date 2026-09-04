package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class EnvironmentRegistryTest {
  @Test
  void selectsExternalAndContainerProvidersIndependently() {
    DefaultEnvironmentRegistry registry = new DefaultEnvironmentRegistry();
    EnvironmentType type = new EnvironmentType("database");
    EnvironmentProvider external = provider("external", EnvironmentMode.EXTERNAL);
    EnvironmentProvider container = provider("container", EnvironmentMode.CONTAINER);

    registry.register(type, EnvironmentMode.EXTERNAL, external);
    registry.register(type, EnvironmentMode.CONTAINER, container);

    assertThat(registry.providerFor(type, EnvironmentMode.EXTERNAL)).isSameAs(external);
    assertThat(registry.providerFor(type, EnvironmentMode.CONTAINER)).isSameAs(container);
  }

  @Test
  void concurrentRegistrationHasExactlyOneWinner() throws Exception {
    DefaultEnvironmentRegistry registry = new DefaultEnvironmentRegistry();
    EnvironmentType type = new EnvironmentType("database");
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<Callable<Boolean>> tasks = new ArrayList<>();
      for (int index = 0; index < 20; index++) {
        int providerIndex = index;
        tasks.add(
            () -> {
              try {
                registry.register(
                    type,
                    EnvironmentMode.EXTERNAL,
                    provider("p" + providerIndex, EnvironmentMode.EXTERNAL));
                return true;
              } catch (IllegalStateException duplicate) {
                return false;
              }
            });
      }
      assertThat(
              executor.invokeAll(tasks).stream()
                  .filter(
                      future -> {
                        try {
                          return future.get();
                        } catch (Exception failure) {
                          throw new AssertionError(failure);
                        }
                      }))
          .hasSize(1);
    }
  }

  @Test
  void missingProviderErrorIsActionable() {
    assertThatThrownBy(
            () ->
                new DefaultEnvironmentRegistry()
                    .providerFor(new EnvironmentType("database"), EnvironmentMode.EXTERNAL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("database/EXTERNAL");
  }

  private static EnvironmentProvider provider(String id, EnvironmentMode mode) {
    return new EnvironmentProvider() {
      public String id() {
        return id;
      }

      public Set<EnvironmentMode> supportedModes() {
        return Set.of(mode);
      }

      public PreflightResult preflight(EnvironmentRequest request) {
        return PreflightResult.from(List.of());
      }

      public EnvironmentResource provision(EnvironmentRequest request) {
        throw new UnsupportedOperationException();
      }

      public java.util.Collection<EnvironmentResource> activeResources() {
        return List.of();
      }

      public void release(String resourceId) {}

      public void cleanup() {}
    };
  }
}
