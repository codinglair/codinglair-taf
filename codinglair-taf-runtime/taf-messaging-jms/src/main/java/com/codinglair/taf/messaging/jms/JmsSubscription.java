package com.codinglair.taf.messaging.jms;

/** Durable topic subscription settings. */
public record JmsSubscription(String name, String selector) {
  public JmsSubscription {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("name must not be blank");
    selector = selector == null || selector.isBlank() ? null : selector;
  }
}
