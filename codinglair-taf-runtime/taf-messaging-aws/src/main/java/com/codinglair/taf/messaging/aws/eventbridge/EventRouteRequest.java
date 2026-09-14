package com.codinglair.taf.messaging.aws.eventbridge;

import java.time.Duration;
import java.util.Objects;

/** A single event and bounded target-observation interval used for route composition. */
public record EventRouteRequest(EventPublishRequest event, Duration timeout) {
  public EventRouteRequest {
    Objects.requireNonNull(event, "event");
    if (timeout == null || timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException("timeout must be positive");
  }
}
