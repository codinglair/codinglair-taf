package com.codinglair.taf.messaging.rabbitmq;

import com.codinglair.taf.messaging.ConsumptionResult;
import com.codinglair.taf.messaging.MessageEnvelope;
import com.codinglair.taf.messaging.MessageQuery;
import com.codinglair.taf.messaging.MessageRecord;
import com.codinglair.taf.messaging.MessagingController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;

/** RabbitMQ controller with explicit topology, routing, and delivery disposition. */
public interface RabbitController extends MessagingController {
  @ControllerAction("Declare RabbitMQ topology")
  void declareTopology(RabbitTopology topology);

  @ControllerAction("Publish routed RabbitMQ message")
  MessageRecord publish(RabbitTopology topology, MessageEnvelope message);

  @ControllerAction("Consume RabbitMQ message with acknowledgment")
  ConsumptionResult consume(MessageQuery query, RabbitAcknowledgment acknowledgment)
      throws InterruptedException;
}
