package com.codinglair.taf.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.messaging.MessageSerializer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Kafka serializer adapter")
class KafkaSerializerAdapterTest {
  @Test
  @DisplayName("preserves TAF serialization and media type")
  void roundTrip() {
    KafkaSerializerAdapter<String> adapter =
        new KafkaSerializerAdapter<>(
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
            });
    assertThat(adapter.deserialize("events", adapter.serialize("events", "value")))
        .isEqualTo("value");
    assertThat(adapter.mediaType()).isEqualTo("text/plain");
  }
}
