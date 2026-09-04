package com.codinglair.taf.messaging.jms;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;

@DisplayName("JMS public values")
class JmsValueContractTest {
  @Nested
  @DisplayName("Validation")
  class Validation {
    @Test
    @DisplayName("rejects blank destinations and subscriptions")
    void rejectsBlankValues() {
      assertThatThrownBy(() -> JmsDestination.queue(" "))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new JmsSubscription("", null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("normalizes a blank native selector")
    void normalizesSelector() {
      assertThat(new JmsSubscription("orders", " ").selector()).isNull();
    }
  }
}
