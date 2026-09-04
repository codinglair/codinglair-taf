package com.codinglair.taf.runtime.core.reporting.abstraction;

import com.codinglair.taf.runtime.core.failure.FailureAnalysis;

/**
 * TestReporter SPI for capturing test execution events.
 *
 * <p>Implementations may be vendor-specific (e.g., Allure) while maintaining the same core contract
 * for test execution reporting.
 */
public interface TestReporter {

  /** Receives authoritative Runtime metadata; adapters must not reclassify it. */
  default void reportFailure(FailureAnalysis analysis) {}

  /**
   * Reports a hierarchical event. The compatibility default exposes completed events through the
   * original flat step contract; hierarchy-aware adapters override this method.
   */
  default void reportEvent(ReportEvent event) {
    if (event.phase() == ReportEvent.Phase.FINISHED) {
      reportStep(TestStep.of(event.name(), event.status(), event.description()));
    }
  }

  /**
   * Begin a test execution.
   *
   * @param test the test being executed
   */
  void beginTest(TafTest test);

  /**
   * End a test execution.
   *
   * @param test the test being executed
   */
  void endTest(TafTest test);

  /**
   * Report a test step.
   *
   * @param step the step being executed
   */
  void reportStep(TestStep step);

  /**
   * Report a test artifact.
   *
   * @param artifact the artifact being reported
   */
  void reportArtifact(TestArtifact artifact);

  /**
   * Get the reporter name.
   *
   * @return the reporter name
   */
  String getName();

  /**
   * Get the number of steps reported by this reporter.
   *
   * @return the number of steps reported
   */
  default int reportedSteps() {
    return 0;
  }

  /**
   * Get the number of artifacts reported by this reporter.
   *
   * @return the number of artifacts reported
   */
  default int reportedArtifacts() {
    return 0;
  }
}
