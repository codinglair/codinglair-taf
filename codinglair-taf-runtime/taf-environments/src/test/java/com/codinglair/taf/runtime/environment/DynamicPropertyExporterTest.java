package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DynamicPropertyExporterTest {
  @Test
  void exportsContainerValuesToSpringCompatibleSinkWithoutFixedPorts() {
    EnvironmentResource resource =
        new EnvironmentResource() {
          @Override
          public String id() {
            return "container";
          }

          @Override
          public EnvironmentType type() {
            return new EnvironmentType("database");
          }

          @Override
          public EnvironmentMode mode() {
            return EnvironmentMode.CONTAINER;
          }

          @Override
          public Map<String, String> properties() {
            return Map.of("service.host", "localhost", "service.port", "49177");
          }

          @Override
          public EnvironmentDiagnostic diagnose() {
            return new EnvironmentDiagnostic(
                EnvironmentStatus.READY, "ready", "", Map.of(), Instant.now());
          }

          @Override
          public void cleanup() {}
        };
    Map<String, Supplier<Object>> registered = new LinkedHashMap<>();

    DynamicPropertyExporter.export(resource, registered::put);

    assertThat(registered).containsOnlyKeys("service.host", "service.port");
    assertThat(registered.get("service.port").get()).isEqualTo("49177");
  }
}
