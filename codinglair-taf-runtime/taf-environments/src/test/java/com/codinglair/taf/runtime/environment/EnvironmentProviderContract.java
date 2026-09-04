package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Reusable contract suite for provider implementations. */
abstract class EnvironmentProviderContract {
  protected abstract EnvironmentProvider provider();

  protected abstract int cleanupCount(EnvironmentResource resource);

  protected EnvironmentRequest request() {
    EnvironmentMode mode = provider().supportedModes().iterator().next();
    return new EnvironmentRequest(
        "contract-resource",
        new EnvironmentType("database"),
        mode,
        Set.of(),
        Map.of(),
        Duration.ofSeconds(5));
  }

  @Test
  void provisionTracksAndReleaseCleansExactlyOnce() {
    EnvironmentProvider provider = provider();
    EnvironmentResource resource = provider.provision(request());

    assertThat(provider.activeResources()).containsExactly(resource);
    provider.release(resource.id());
    provider.release(resource.id());

    assertThat(cleanupCount(resource)).isOne();
    assertThat(provider.activeResources()).isEmpty();
  }

  @Test
  void cleanupIsIdempotent() {
    EnvironmentProvider provider = provider();
    EnvironmentResource resource = provider.provision(request());

    provider.cleanup();
    provider.cleanup();

    assertThat(cleanupCount(resource)).isOne();
  }

  @Test
  void concurrentReleaseStillCleansExactlyOnce() throws Exception {
    EnvironmentProvider provider = provider();
    EnvironmentResource resource = provider.provision(request());
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var tasks =
          java.util.stream.IntStream.range(0, 32)
              .<java.util.concurrent.Callable<Void>>mapToObj(
                  index ->
                      () -> {
                        provider.release(resource.id());
                        return null;
                      })
              .toList();
      for (var result : executor.invokeAll(tasks)) {
        result.get();
      }
    }
    assertThat(cleanupCount(resource)).isOne();
  }

  @Test
  void unsupportedModeIsAnEnvironmentFailure() {
    EnvironmentMode unsupported =
        provider().supportedModes().contains(EnvironmentMode.EXTERNAL)
            ? EnvironmentMode.CONTAINER
            : EnvironmentMode.EXTERNAL;
    EnvironmentRequest request =
        new EnvironmentRequest(
            "wrong-mode",
            new EnvironmentType("database"),
            unsupported,
            Set.of(),
            Map.of(),
            Duration.ofSeconds(1));

    assertThatThrownBy(() -> provider().provision(request))
        .isInstanceOf(EnvironmentProvisioningException.class)
        .extracting(failure -> ((EnvironmentProvisioningException) failure).error().type())
        .isEqualTo(com.codinglair.taf.core.Error.ErrorType.ENVIRONMENT_ISSUE);
  }
}
