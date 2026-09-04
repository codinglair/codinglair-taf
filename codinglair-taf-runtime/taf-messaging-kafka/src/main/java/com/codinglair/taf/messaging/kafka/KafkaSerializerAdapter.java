package com.codinglair.taf.messaging.kafka;

import com.codinglair.taf.messaging.MessageSerializer;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

/** Adapts a TAF serializer to Kafka's serializer and deserializer contracts. */
public final class KafkaSerializerAdapter<T> implements Serializer<T>, Deserializer<T> {
  private final MessageSerializer<T> delegate;

  public KafkaSerializerAdapter(MessageSerializer<T> delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public byte[] serialize(String topic, T data) {
    return delegate.serialize(data);
  }

  @Override
  public T deserialize(String topic, byte[] data) {
    return delegate.deserialize(data);
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {}

  @Override
  public void close() {}

  public String mediaType() {
    return delegate.mediaType();
  }
}
