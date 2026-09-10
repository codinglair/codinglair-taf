package com.codinglair.taf.messaging.aws.eventbridge;

import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import java.util.List;

public interface EventBridgeController extends TestController {
  @ControllerAction("Publish EventBridge events")
  EventPublishResult publish(List<EventPublishRequest> events);

  /**
   * Releases the session-owned SDK client. Lifecycle cleanup is intentionally not reported as a
   * user-level controller action.
   */
  @Override
  void close();
}
