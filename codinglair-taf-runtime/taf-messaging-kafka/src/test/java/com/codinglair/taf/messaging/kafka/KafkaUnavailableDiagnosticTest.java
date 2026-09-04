package com.codinglair.taf.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Kafka unavailable-broker diagnostics")
class KafkaUnavailableDiagnosticTest {
  @Test
  @DisplayName("classifies initialization failure without exposing endpoint details")
  void unavailableBroker() {
    KafkaControllerSettings settings = new KafkaControllerSettings();
    settings.setBootstrapServers(List.of("127.0.0.1:1"));
    settings.setOperationTimeout(Duration.ofMillis(200));
    DefaultKafkaController controller = new DefaultKafkaController("unavailable", settings);
    ControllerContext context =
        new ControllerContext(
            "session",
            EnvironmentAccess.unavailable(),
            new ArtifactCollector(TafTest.of("test", getClass().getName()), "session", "test"));

    KafkaControllerException failure =
        assertThrows(KafkaControllerException.class, () -> controller.initialize(context));

    assertThat(controller.state()).isEqualTo(ControllerState.FAILED);
    assertThat(failure.operation()).isEqualTo("initialize");
    assertThat(failure.getMessage()).doesNotContain("127.0.0.1");
    assertThat(controller.health().status()).isEqualTo(HealthResult.Status.UNAVAILABLE);
    controller.close();
    controller.close();
    assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
  }
}
