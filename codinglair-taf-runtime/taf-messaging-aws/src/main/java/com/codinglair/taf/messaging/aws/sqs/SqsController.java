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

  @ControllerAction("Wait for SQS message")
  ReceivedSqsMessage awaitMessage(SqsReceiveRequest request) throws InterruptedException;

  @ControllerAction("Assert no matching SQS message")
  void assertNoMatchingMessage(SqsReceiveRequest request) throws InterruptedException;

  @ControllerAction("Assert SQS message body")
  void assertBody(ReceivedSqsMessage message, String expectedBody);

  @ControllerAction("Assert SQS message attributes")
  void assertAttributes(
      ReceivedSqsMessage message, java.util.Map<String, String> expectedAttributes);

  @ControllerAction("Collect SQS message evidence")
  SqsMessageEvidence evidence(ReceivedSqsMessage message);

  @ControllerAction("Acknowledge SQS message")
  void acknowledge(ReceivedSqsMessage message);

  @ControllerAction("Change SQS message visibility")
  void changeVisibility(ReceivedSqsMessage message, Duration visibility);

  @ControllerAction("Inspect SQS message visibility")
  SqsVisibility visibility(ReceivedSqsMessage message);

  @ControllerAction("Inspect SQS queue diagnostics")
  SqsQueueDiagnostics diagnostics();

  /**
   * Releases the session-owned SDK client. Lifecycle cleanup is intentionally not reported as a
   * user-level controller action.
   */
  @Override
  void close();
}
