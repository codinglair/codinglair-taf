package com.codinglair.taf.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.MessageEnvelope;
import com.codinglair.taf.messaging.MessageRecord;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Kafka diagnostic evidence bounds")
class KafkaEvidenceBoundTest {
  @Nested
  @DisplayName("Concurrent retention")
  class ConcurrentRetention {
    @Test
    @DisplayName("never retains more than the configured maximum")
    void boundedEvidence() throws Exception {
      KafkaControllerSettings settings = new KafkaControllerSettings();
      settings.setMaximumBufferedRecords(3);
      DefaultKafkaController controller = new DefaultKafkaController("bounded", settings);

      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int index = 0; index < 100; index++) {
          long offset = index;
          executor.submit(() -> controller.remember("consume", record(offset)));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      }

      String content =
          controller
              .collectArtifacts(ArtifactReason.DIAGNOSTIC)
              .findFirst()
              .orElseThrow()
              .content();
      assertThat(content.lines()).hasSize(3);
      controller.close();
      assertThat(controller.collectArtifacts(ArtifactReason.DIAGNOSTIC)).isEmpty();
    }
  }

  private static MessageRecord record(long offset) {
    return new MessageRecord(
        new MessageEnvelope(new byte[] {1}),
        Instant.EPOCH,
        Map.of("topic", "events", "partition", 0, "offset", offset, "consumerGroup", "group"));
  }
}
