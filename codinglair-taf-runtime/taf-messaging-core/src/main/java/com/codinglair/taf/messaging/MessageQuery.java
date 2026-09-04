package com.codinglair.taf.messaging;

import java.time.Duration;
import java.util.Objects;

/** A bounded request to consume one matching message from a logical destination. */
public record MessageQuery(String destination, MessageSelector selector, Duration timeout) {
  public MessageQuery {
    Objects.requireNonNull(destination, "destination must not be null");
    if (destination.isBlank()) throw new IllegalArgumentException("destination must not be blank");
    selector = Objects.requireNonNull(selector, "selector must not be null");
    timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    if (timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException("timeout must be positive");
  }
}
