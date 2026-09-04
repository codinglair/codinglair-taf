package com.codinglair.taf.messaging.jms;

import java.util.Objects;

/** Provider-neutral JMS destination description. */
public record JmsDestination(Type type, String name) {
  public JmsDestination {
    Objects.requireNonNull(type, "type");
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("name must not be blank");
  }

  public enum Type {
    QUEUE,
    TOPIC
  }

  public static JmsDestination queue(String name) {
    return new JmsDestination(Type.QUEUE, name);
  }

  public static JmsDestination topic(String name) {
    return new JmsDestination(Type.TOPIC, name);
  }
}
