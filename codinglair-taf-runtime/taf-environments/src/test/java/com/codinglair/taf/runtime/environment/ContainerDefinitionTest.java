package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class ContainerDefinitionTest {
  @Test
  void rejectsInvalidPortsAndUnboundedLogs() {
    assertThatThrownBy(() -> definition(List.of(0), 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> definition(List.of(8080), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void propertyResolversRejectInvalidEndpointInputs() {
    assertThatThrownBy(() -> ContainerProperty.mappedPort(65_536))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ContainerProperty.endpoint(" ", 8080))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void definitionStringNeverExposesEnvironmentValuesOrCommands() {
    String canary = "canary-value-93";
    ContainerDefinition definition =
        new ContainerDefinition(
            DockerImageName.parse("testcontainers/helloworld:1.1.0"),
            List.of(8080),
            Map.of("SERVICE_TOKEN", canary),
            List.of("--token=" + canary),
            Wait.forListeningPort(),
            Map.of("service.port", ContainerProperty.mappedPort(8080)),
            100);

    assertThat(definition.toString())
        .doesNotContain(canary)
        .contains("SERVICE_TOKEN")
        .contains("commandArguments=1");
  }

  private static ContainerDefinition definition(List<Integer> ports, int logLimit) {
    return new ContainerDefinition(
        DockerImageName.parse("testcontainers/helloworld:1.1.0"),
        ports,
        Map.of(),
        List.of(),
        Wait.forListeningPort(),
        Map.of(),
        logLimit);
  }
}
