package com.codinglair.taf.messaging;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Common correlation and content matching without imposing broker-native selector semantics. */
public record MessageSelector(
    Optional<Correlation> correlation, Predicate<MessageRecord> predicate) {
  public MessageSelector {
    correlation = Objects.requireNonNull(correlation, "correlation must not be null");
    predicate = Objects.requireNonNull(predicate, "predicate must not be null");
  }

  public static MessageSelector any() {
    return new MessageSelector(Optional.empty(), ignored -> true);
  }

  public static MessageSelector correlated(Correlation correlation) {
    return new MessageSelector(Optional.of(correlation), ignored -> true);
  }

  public boolean matches(MessageRecord record) {
    Objects.requireNonNull(record, "record must not be null");
    return correlation
            .map(value -> record.envelope().correlation().filter(value::equals).isPresent())
            .orElse(true)
        && predicate.test(record);
  }
}
