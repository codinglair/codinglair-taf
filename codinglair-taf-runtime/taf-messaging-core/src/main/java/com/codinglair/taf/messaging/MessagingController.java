package com.codinglair.taf.messaging;

import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;

/** Common messaging capability implemented by technology-specific adapters. */
public interface MessagingController extends TestController {
  @ControllerAction("Publish message")
  MessageRecord publish(String destination, MessageEnvelope message);

  @ControllerAction("Consume matching message")
  ConsumptionResult consume(MessageQuery query) throws InterruptedException;

  @ControllerAction("Assert no matching message")
  default ConsumptionResult assertNoMatch(MessageQuery query) throws InterruptedException {
    ConsumptionResult result = consume(query);
    if (result.status() == ConsumptionResult.Status.MATCHED)
      throw new AssertionError("Unexpected matching message received from " + query.destination());
    return result;
  }
}
