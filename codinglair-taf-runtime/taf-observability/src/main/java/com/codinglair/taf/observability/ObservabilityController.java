package com.codinglair.taf.observability;

import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import java.time.Duration;
import java.util.function.Predicate;

/** Typed controller for bounded querying and eventual assertion of observability signals. */
public interface ObservabilityController extends TestController {
  @ControllerAction("Query logs, metrics, or traces")
  TelemetryQueryResult query(TelemetryQuery query);

  /**
   * Polls until the predicate matches or the timeout expires. Provider outages fail immediately;
   * successful empty queries continue polling until timeout.
   */
  @ControllerAction("Eventually assert logs, metrics, or traces")
  TelemetryAssertionResult assertEventually(
      TelemetryQuery query,
      Predicate<TelemetryQueryResult> expectation,
      Duration timeout,
      Duration pollInterval);
}
