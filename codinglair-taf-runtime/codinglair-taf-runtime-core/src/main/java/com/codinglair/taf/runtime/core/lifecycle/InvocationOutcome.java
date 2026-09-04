package com.codinglair.taf.runtime.core.lifecycle;

import java.util.Objects;

/** Result supplied by a lifecycle owner when its invocation ends. */
public record InvocationOutcome(Status status, Throwable failure) {
  public InvocationOutcome {
    Objects.requireNonNull(status, "status");
    if (status == Status.PASSED && failure != null) {
      throw new IllegalArgumentException("A passed invocation cannot have a failure");
    }
  }

  public static InvocationOutcome passed() {
    return new InvocationOutcome(Status.PASSED, null);
  }

  public static InvocationOutcome failed(Throwable failure) {
    return new InvocationOutcome(Status.FAILED, Objects.requireNonNull(failure, "failure"));
  }

  public static InvocationOutcome cancelled(Throwable failure) {
    return new InvocationOutcome(Status.CANCELLED, Objects.requireNonNull(failure, "failure"));
  }

  public static InvocationOutcome setupFailed(Throwable failure) {
    return new InvocationOutcome(Status.SETUP_FAILED, Objects.requireNonNull(failure, "failure"));
  }

  public enum Status {
    PASSED,
    FAILED,
    CANCELLED,
    SETUP_FAILED
  }
}
