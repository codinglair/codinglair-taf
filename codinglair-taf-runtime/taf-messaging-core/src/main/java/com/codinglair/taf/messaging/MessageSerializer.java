package com.codinglair.taf.messaging;

/** Pluggable message payload serialization boundary. */
public interface MessageSerializer<T> {
  byte[] serialize(T value);

  T deserialize(byte[] payload);

  String mediaType();
}
