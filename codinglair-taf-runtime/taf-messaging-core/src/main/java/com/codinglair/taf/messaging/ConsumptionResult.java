package com.codinglair.taf.messaging;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Explicit consume outcome; a timeout is a deterministic no-match result, not a null value. */
public record ConsumptionResult(Status status, Optional<MessageRecord> record, Duration elapsed) {
  public enum Status {
    MATCHED,
    NO_MATCH
  }

  public ConsumptionResult {
    status = Objects.requireNonNull(status, "status must not be null");
    record = Objects.requireNonNull(record, "record must not be null");
    elapsed = Objects.requireNonNull(elapsed, "elapsed must not be null");
    if (elapsed.isNegative()) throw new IllegalArgumentException("elapsed must not be negative");
    if ((status == Status.MATCHED) != record.isPresent())
      throw new IllegalArgumentException("MATCHED requires a record and NO_MATCH forbids one");
  }

  public static ConsumptionResult matched(MessageRecord record, Duration elapsed) {
    return new ConsumptionResult(Status.MATCHED, Optional.of(record), elapsed);
  }

  public static ConsumptionResult noMatch(Duration elapsed) {
    return new ConsumptionResult(Status.NO_MATCH, Optional.empty(), elapsed);
  }
}
