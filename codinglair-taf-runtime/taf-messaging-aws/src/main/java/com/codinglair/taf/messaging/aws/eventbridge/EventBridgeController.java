package com.codinglair.taf.messaging.aws.eventbridge;

import com.codinglair.taf.messaging.aws.sqs.SqsController;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import java.util.List;

public interface EventBridgeController extends TestController {
  @ControllerAction("Publish EventBridge events")
  EventPublishResult publish(List<EventPublishRequest> events);

  @ControllerAction("Verify EventBridge route to SQS")
  EventRouteResult verifyRoute(EventRouteRequest request, SqsController target)
      throws InterruptedException;

  @ControllerAction("Assert EventBridge event is not routed to SQS")
  EventPublishResult assertNotRouted(EventRouteRequest request, SqsController target)
      throws InterruptedException;

  /**
   * Releases the session-owned SDK client. Lifecycle cleanup is intentionally not reported as a
   * user-level controller action.
   */
  @Override
  void close();
}
