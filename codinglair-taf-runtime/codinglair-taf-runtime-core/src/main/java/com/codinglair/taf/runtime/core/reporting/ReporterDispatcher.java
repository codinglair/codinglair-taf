package com.codinglair.taf.runtime.core.reporting;

import com.codinglair.taf.runtime.core.failure.FailureAnalysis;
import com.codinglair.taf.runtime.core.reporting.abstraction.ReportEvent;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestReporter;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestStep;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReporterDispatcher catches and handles reporter failures to protect the authoritative
 * ArtifactCollector state from corruption.
 *
 * <p>This dispatcher wraps TestReporter implementations and ensures that exceptions thrown by
 * reporters do not corrupt the collector state. All reporter failures are logged and reported, but
 * do not affect the authoritative test execution state.
 */
public class ReporterDispatcher {

  private final TestReporter reporter;
  private final RedactionService redactionService;
  private final Queue<Throwable> failureLog = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean failed = new AtomicBoolean(false);
  private final AtomicInteger failureCount = new AtomicInteger(0);

  public ReporterDispatcher(TestReporter reporter, RedactionService redactionService) {
    this.reporter = reporter;
    this.redactionService = redactionService;
  }

  /**
   * Begin test execution.
   *
   * @param test the test being executed
   */
  public void beginTest(TafTest test) {
    try {
      reporter.beginTest(test);
    } catch (Exception e) {
      catchFailure(e);
    }
  }

  /**
   * Report a test step.
   *
   * @param step the step being executed
   */
  public void reportStep(TestStep step) {
    try {
      reporter.reportStep(step);
    } catch (Exception e) {
      catchFailure(e);
    }
  }

  public void reportEvent(ReportEvent event) {
    try {
      reporter.reportEvent(event);
    } catch (Exception e) {
      catchFailure(e);
    }
  }

  public void reportFailure(FailureAnalysis analysis) {
    try {
      reporter.reportFailure(analysis);
    } catch (Exception failure) {
      catchFailure(failure);
    }
  }

  /**
   * Report a test artifact.
   *
   * @param artifact the artifact being reported
   */
  public void reportArtifact(TestArtifact artifact) {
    try {
      String name = redactionService.redact(artifact.name());
      String type = redactionService.redact(artifact.type());
      String content = redactionService.redact(artifact.content());
      String contentType = redactionService.redact(artifact.contentType());
      String step = redactionService.redact(artifact.stepName());
      reporter.reportArtifact(
          TestArtifact.of(
              name, type, content, contentType, step, TestArtifact.computeHash(content)));
    } catch (Exception e) {
      catchFailure(e);
    }
  }

  /**
   * End test execution.
   *
   * @param test the test being executed
   */
  public void endTest(TafTest test) {
    try {
      reporter.endTest(test);
    } catch (Exception e) {
      catchFailure(e);
    }
  }

  /**
   * Get the reporter name.
   *
   * @return the reporter name
   */
  public String getName() {
    return reporter.getName();
  }

  /**
   * Get the number of steps reported by this reporter.
   *
   * @return the number of steps reported
   */
  public int reportedSteps() {
    return reporter.reportedSteps();
  }

  /**
   * Get the number of artifacts reported by this reporter.
   *
   * @return the number of artifacts reported
   */
  public int reportedArtifacts() {
    return reporter.reportedArtifacts();
  }

  /**
   * Get the failure log.
   *
   * @return the failure log
   */
  public Queue<Throwable> getFailureLog() {
    return failureLog;
  }

  /**
   * Check if the reporter has failed.
   *
   * @return true if the reporter has failed
   */
  public boolean hasFailed() {
    return failed.get();
  }

  /**
   * Get the number of failures.
   *
   * @return the number of failures
   */
  public int getFailureCount() {
    return failureCount.get();
  }

  /**
   * Catch a failure and log it.
   *
   * @param failure the failure
   */
  private void catchFailure(Throwable failure) {
    failureLog.offer(failure);
    failureCount.incrementAndGet();
    failed.set(true);
  }
}
