package com.codinglair.taf.messaging.rabbitmq;

/** Immutable RabbitMQ exchange, queue, and routing-key topology. */
public record RabbitTopology(
    String exchange, String queue, String routingKey, String exchangeType, boolean durable) {
  public RabbitTopology {
    requireText(exchange, "exchange");
    requireText(queue, "queue");
    requireText(routingKey, "routingKey");
    requireText(exchangeType, "exchangeType");
  }

  public RabbitTopology(String exchange, String queue, String routingKey) {
    this(exchange, queue, routingKey, "direct", false);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
  }
}
