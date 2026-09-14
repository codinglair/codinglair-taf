package com.codinglair.taf.messaging.aws.common;

import java.time.Duration;
import java.util.Objects;

/** Executes retry-safe work within one attempt/deadline budget. */
public final class AwsOperationExecutor {
  private final AwsClock clock;

  public AwsOperationExecutor(AwsClock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public <T> T execute(
      String service,
      String operation,
      AwsOperationPolicy policy,
      boolean retrySafe,
      Operation<T> work)
      throws InterruptedException {
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(work, "work");
    AwsOperationBudget budget =
        new AwsOperationBudget(clock, policy.getOperationTimeout(), policy.getRetryAttempts());
    RuntimeException last = null;
    while (budget.tryAcquireAttempt()) {
      if (Thread.currentThread().isInterrupted()) throw interrupted(operation);
      try {
        return work.run();
      } catch (RuntimeException failure) {
        last = failure;
        var classification = AwsFailureClassifier.classify(failure);
        if (!retrySafe || !classification.retryable() || budget.exhausted())
          throw new AwsControllerException(service, operation, classification, failure);
        Duration delay = minimum(policy.getPollInterval(), budget.remaining());
        if (!delay.isZero()) clock.sleep(delay);
      }
    }
    throw new AwsControllerException(
        service,
        operation,
        new AwsFailureClassifier.Classification(AwsFailureCategory.TIMEOUT, false),
        last == null ? new IllegalStateException("operation budget exhausted") : last);
  }

  private static Duration minimum(Duration first, Duration second) {
    return first.compareTo(second) <= 0 ? first : second;
  }

  private static InterruptedException interrupted(String operation) {
    Thread.currentThread().interrupt();
    return new InterruptedException("AWS operation interrupted: " + operation);
  }

  @FunctionalInterface
  public interface Operation<T> {
    T run();
  }
}
