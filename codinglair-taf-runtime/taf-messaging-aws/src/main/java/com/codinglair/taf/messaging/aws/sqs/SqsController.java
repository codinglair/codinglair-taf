package com.codinglair.taf.messaging.aws.sqs;

import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import java.time.Duration;
import java.util.List;

public interface SqsController extends TestController {
  @ControllerAction("Send SQS message")
  SqsSendResult send(SqsSendRequest request);

  @ControllerAction("Receive SQS messages")
  List<ReceivedSqsMessage> receive(SqsReceiveRequest request) throws InterruptedException;

  @ControllerAction("Acknowledge SQS message")
  void acknowledge(ReceivedSqsMessage message);

  @ControllerAction("Change SQS message visibility")
  void changeVisibility(ReceivedSqsMessage message, Duration visibility);

  /**
   * Releases the session-owned SDK client. Lifecycle cleanup is intentionally not reported as a
   * user-level controller action.
   */
  @Override
  void close();
}
