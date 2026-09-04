package com.codinglair.taf.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RabbitMQ unavailable-broker diagnostics")
class RabbitUnavailableDiagnosticTest {
  @Test
  @DisplayName("classifies initialization failure without exposing endpoint or password")
  void unavailableBroker() {
    RabbitControllerSettings settings = new RabbitControllerSettings();
    settings.setAddresses("127.0.0.1:1");
    settings.setPasswordReference("secret://env/RABBITMQ_PASSWORD");
    settings.setOperationTimeout(Duration.ofMillis(200));
    DefaultRabbitController controller =
        new DefaultRabbitController(
            "unavailable", settings, new TestSecretManager("canary-secret"));
    ControllerContext context =
        new ControllerContext(
            "session",
            EnvironmentAccess.unavailable(),
            new ArtifactCollector(TafTest.of("test", getClass().getName()), "session", "test"));

    RabbitControllerException failure =
        assertThrows(RabbitControllerException.class, () -> controller.initialize(context));

    assertThat(controller.state()).isEqualTo(ControllerState.FAILED);
    assertThat(failure.operation()).isEqualTo("initialize");
    assertThat(failure.toString()).doesNotContain("127.0.0.1", "canary-secret");
    assertThat(controller.health().status()).isEqualTo(HealthResult.Status.UNAVAILABLE);
    controller.close();
    controller.close();
    assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
  }
}
