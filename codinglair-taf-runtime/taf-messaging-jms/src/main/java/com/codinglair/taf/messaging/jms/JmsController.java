package com.codinglair.taf.messaging.jms;

import com.codinglair.taf.messaging.*;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;

/** Jakarta JMS controller retaining queue, topic, selector, and durable-subscription semantics. */
public interface JmsController extends MessagingController {
  @ControllerAction("Publish JMS message")
  MessageRecord publish(JmsDestination destination, MessageEnvelope message);

  @ControllerAction("Consume JMS message")
  ConsumptionResult consume(JmsDestination destination, String selector, MessageQuery query)
      throws InterruptedException;

  @ControllerAction("Consume durable JMS subscription")
  ConsumptionResult consumeDurable(
      JmsDestination topic, JmsSubscription subscription, MessageQuery query)
      throws InterruptedException;
}
