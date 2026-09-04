package com.codinglair.taf.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RabbitMQ diagnostic evidence bounds")
class RabbitEvidenceBoundTest {
  @Test
  @DisplayName("bounds concurrent sanitized topology evidence")
  void boundedEvidence() throws Exception {
    RabbitControllerSettings settings = new RabbitControllerSettings();
    settings.setMaximumBufferedRecords(3);
    DefaultRabbitController controller =
        new DefaultRabbitController("bounded", settings, TestSecretManager.GUEST);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int index = 0; index < 100; index++)
        executor.submit(() -> controller.remember("consume", "queue", "exchange", "key", "ACK"));
      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
    String content =
        controller.collectArtifacts(ArtifactReason.DIAGNOSTIC).findFirst().orElseThrow().content();
    assertThat(content.lines()).hasSize(3);
    assertThat(content).doesNotContain("password", "guest");
    controller.close();
    assertThat(controller.collectArtifacts(ArtifactReason.DIAGNOSTIC)).isEmpty();
  }
}
