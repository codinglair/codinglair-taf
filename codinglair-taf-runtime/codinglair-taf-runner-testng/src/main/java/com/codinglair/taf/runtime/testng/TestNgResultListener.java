package com.codinglair.taf.runtime.testng;

/** Provider-neutral consumer of completed TestNG logical execution results. */
@FunctionalInterface
public interface TestNgResultListener {
  void onResult(TestNgExecutionResult result);
}
