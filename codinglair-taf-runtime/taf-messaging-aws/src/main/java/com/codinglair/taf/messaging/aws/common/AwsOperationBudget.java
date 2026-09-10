package com.codinglair.taf.messaging.aws.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable deadline plus an attempt bound for one AWS operation. */
public final class AwsOperationBudget {
  private final AwsClock clock;
  private final Instant deadline;
  private final int maximumAttempts;
  private int attempts;

  public AwsOperationBudget(AwsClock clock, Duration timeout, int retryAttempts) {
    this.clock = Objects.requireNonNull(clock, "clock");
    if (timeout == null || timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("timeout must be positive");
    if (retryAttempts < 0) throw new IllegalArgumentException("retryAttempts must not be negative");
    deadline = clock.now().plus(timeout);
    maximumAttempts = Math.addExact(retryAttempts, 1);
  }

  public synchronized boolean tryAcquireAttempt() {
    if (attempts >= maximumAttempts || !clock.now().isBefore(deadline)) return false;
    attempts++;
    return true;
  }

  public synchronized int attempts() {
    return attempts;
  }

  public Duration remaining() {
    Duration remaining = Duration.between(clock.now(), deadline);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  public boolean exhausted() {
    return attempts() >= maximumAttempts || remaining().isZero();
  }
}
