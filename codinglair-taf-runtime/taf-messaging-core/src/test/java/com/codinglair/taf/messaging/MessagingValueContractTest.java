package com.codinglair.taf.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Messaging value contracts")
class MessagingValueContractTest {
  @Nested
  @DisplayName("Immutability")
  class Immutability {
    @Test
    @DisplayName("defensively copies payload bytes and headers")
    void envelopeCopiesValues() {
      byte[] payload = {1, 2};
      MessageEnvelope envelope =
          new MessageEnvelope(payload, Map.of("key", "value"), Optional.empty());
      payload[0] = 9;
      byte[] returned = envelope.payload();
      returned[1] = 9;
      assertThat(envelope.payload()).containsExactly(1, 2);
      assertThrows(UnsupportedOperationException.class, () -> envelope.headers().put("x", "y"));
    }
  }

  @Nested
  @DisplayName("Validation")
  class Validation {
    @Test
    @DisplayName("rejects unbounded and invalid queries")
    void invalidQuery() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new MessageQuery("events", MessageSelector.any(), Duration.ZERO));
      assertThrows(
          IllegalArgumentException.class,
          () -> new MessageQuery(" ", MessageSelector.any(), Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("rejects inconsistent consume outcomes")
    void inconsistentOutcome() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ConsumptionResult(
                  ConsumptionResult.Status.MATCHED, Optional.empty(), Duration.ZERO));
    }
  }

  @Test
  @DisplayName("supports a provider-independent serializer")
  void serializer() {
    MessageSerializer<String> serializer =
        new MessageSerializer<>() {
          public byte[] serialize(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
          }

          public String deserialize(byte[] payload) {
            return new String(payload, StandardCharsets.UTF_8);
          }

          public String mediaType() {
            return "text/plain";
          }
        };
    assertThat(serializer.deserialize(serializer.serialize("value"))).isEqualTo("value");
    assertThat(serializer.mediaType()).isEqualTo("text/plain");
  }
}
