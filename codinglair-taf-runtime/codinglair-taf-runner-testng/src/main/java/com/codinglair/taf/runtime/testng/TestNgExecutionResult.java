package com.codinglair.taf.runtime.testng;

import java.util.List;
import java.util.Objects;

/** Immutable current-execution history for one logical TestNG test invocation. */
public record TestNgExecutionResult(
    String testId, String testName, String className, List<TestNgAttemptResult> attempts) {

  public TestNgExecutionResult {
    Objects.requireNonNull(testId, "testId");
    Objects.requireNonNull(testName, "testName");
    Objects.requireNonNull(className, "className");
    attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
  }
}
