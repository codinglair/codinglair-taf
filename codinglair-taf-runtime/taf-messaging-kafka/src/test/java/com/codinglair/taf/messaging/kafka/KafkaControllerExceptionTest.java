package com.codinglair.taf.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Kafka controller structured failures")
class KafkaControllerExceptionTest {
  @Nested
  @DisplayName("Cause diagnostics")
  class CauseDiagnostics {
    @Test
    @DisplayName("preserves the sanitized cause chain and actionable messages")
    void sanitizedCauseChain() {
      IllegalArgumentException root =
          new IllegalArgumentException("authentication token=top-secret rejected");
      IllegalStateException outer =
          new IllegalStateException("broker localhost:9092 unavailable", root);

      KafkaControllerException failure =
          new KafkaControllerException("initialize", "verify broker readiness", outer);

      assertThat(failure.getCause().getMessage())
          .contains("IllegalStateException", "broker [ENDPOINT] unavailable")
          .doesNotContain("localhost:9092");
      assertThat(failure.getCause().getCause().getMessage())
          .contains("IllegalArgumentException", "token=[REDACTED]")
          .doesNotContain("top-secret");
    }

    @Test
    @DisplayName("handles cyclic cause graphs without recursion failure")
    void cyclicCause() {
      CyclicFailure cyclic = new CyclicFailure("loop");

      KafkaControllerException failure =
          new KafkaControllerException("consume", "inspect broker", cyclic);

      assertThat(failure.getCause().getCause().getMessage()).contains("cause cycle");
    }
  }

  private static final class CyclicFailure extends RuntimeException {
    private CyclicFailure(String message) {
      super(message);
    }

    @Override
    public synchronized Throwable getCause() {
      return this;
    }
  }
}
