package com.codinglair.taf.messaging.kafka;

/** Explicit policy governing whether controller operations may create missing topics. */
public enum KafkaTopicPolicy {
  REQUIRE_EXISTING,
  CREATE_IF_MISSING
}
