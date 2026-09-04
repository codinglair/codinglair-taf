package com.codinglair.taf.messaging.kafka;

@FunctionalInterface
public interface KafkaControllerFactory {
  KafkaController create(String name);
}
