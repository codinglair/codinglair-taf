package com.codinglair.taf.messaging.rabbitmq;

@FunctionalInterface
public interface RabbitControllerFactory {
  RabbitController create(String name);
}
