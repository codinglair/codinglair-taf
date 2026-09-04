package com.codinglair.taf.messaging.kafka;

import com.codinglair.taf.messaging.ConsumptionResult;
import com.codinglair.taf.messaging.MessageQuery;
import com.codinglair.taf.messaging.MessagingController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;

/** Kafka-specific messaging controller with explicit consumer-group selection. */
public interface KafkaController extends MessagingController {
  @ControllerAction("Consume Kafka message with consumer group")
  ConsumptionResult consume(MessageQuery query, String consumerGroup) throws InterruptedException;
}
