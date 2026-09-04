package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@EnabledIfSystemProperty(named = "taf.containers.enabled", matches = "true")
class TestcontainersSmokeTest {
  @Test
  void parallelRealContainersReceiveDifferentRandomHostPortsAndCleanup() throws Exception {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required for the Testcontainers smoke contract");
    EnvironmentType type = new EnvironmentType("mock-server");
    DockerImageName image = DockerImageName.parse("testcontainers/helloworld:1.1.0");
    ContainerDefinition definition =
        new ContainerDefinition(
            image,
            List.of(8080),
            Map.of(),
            List.of(),
            Wait.forHttp("/").forPort(8080),
            Map.of(
                "mock.host", ContainerProperty.host(),
                "mock.port", ContainerProperty.mappedPort(8080)),
            16_384);
    ContainerLifecycleCoordinator coordinator = new ContainerLifecycleCoordinator();
    AbstractTestcontainersEnvironmentProvider provider =
        new AbstractTestcontainersEnvironmentProvider(
            type, definition, ContainerImagePolicy.allow(image), coordinator) {
          @Override
          public String id() {
            return "helloworld";
          }
        };
    EnvironmentRequest firstRequest = request("first", type);
    EnvironmentRequest secondRequest = request("second", type);

    assertThat(provider.preflight(firstRequest).status()).isEqualTo(EnvironmentStatus.READY);

    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Callable<EnvironmentResource>> tasks =
          List.of(() -> provider.provision(firstRequest), () -> provider.provision(secondRequest));
      var results = executor.invokeAll(tasks);
      EnvironmentResource first = results.get(0).get();
      EnvironmentResource second = results.get(1).get();
      assertThat(first.properties().get("mock.port"))
          .isNotEqualTo(second.properties().get("mock.port"));
      provider.cleanup();
      assertThat(first.diagnose().status()).isEqualTo(EnvironmentStatus.UNAVAILABLE);
      assertThat(second.diagnose().status()).isEqualTo(EnvironmentStatus.UNAVAILABLE);
    }
  }

  private static EnvironmentRequest request(String name, EnvironmentType type) {
    return new EnvironmentRequest(
        name,
        type,
        EnvironmentMode.CONTAINER,
        Set.of(),
        Map.of(ContainerLifecycle.PROPERTY, "isolated"),
        Duration.ofSeconds(60));
  }
}
