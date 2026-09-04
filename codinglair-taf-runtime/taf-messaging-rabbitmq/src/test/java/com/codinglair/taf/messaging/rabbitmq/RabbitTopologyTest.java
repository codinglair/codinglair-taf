package com.codinglair.taf.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RabbitMQ topology contract")
class RabbitTopologyTest {
  @Nested
  @DisplayName("Validation")
  class Validation {
    @Test
    @DisplayName("preserves explicit exchange queue and routing key")
    void values() {
      RabbitTopology topology = new RabbitTopology("orders", "orders.created", "created");
      assertThat(topology.exchange()).isEqualTo("orders");
      assertThat(topology.queue()).isEqualTo("orders.created");
      assertThat(topology.routingKey()).isEqualTo("created");
      assertThat(topology.exchangeType()).isEqualTo("direct");
    }

    @Test
    @DisplayName("rejects blank topology names")
    void blanks() {
      assertThrows(IllegalArgumentException.class, () -> new RabbitTopology(" ", "queue", "key"));
    }
  }
}
