package com.codinglair.taf.messaging.rabbitmq;

/** Explicit disposition applied after a RabbitMQ delivery matches a query. */
public enum RabbitAcknowledgment {
  ACK,
  NACK_REQUEUE,
  NACK_DISCARD
}
