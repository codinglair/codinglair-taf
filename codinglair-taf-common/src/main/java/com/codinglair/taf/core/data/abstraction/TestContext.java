package com.codinglair.taf.core.data.abstraction;

import com.codinglair.taf.core.data.dao.abstraction.DataProvider;

/**
 * Test context abstraction for holding test data and results.
 *
 * @param <I> The type of input data
 * @param <O> The type of output data
 */
public class TestContext<I, O> {
  protected final DataProvider<String, I> inputsProvider;
  protected final DataProvider<String, O> expectedOutputsProvider;

  public TestContext(
      DataProvider<String, I> inputsProvider, DataProvider<String, O> expectedOutputsProvider) {
    this.inputsProvider = inputsProvider;
    this.expectedOutputsProvider = expectedOutputsProvider;
  }

  public DataProvider<String, I> getInputsProvider() {
    return inputsProvider;
  }

  public DataProvider<String, O> getExpectedOutputsProvider() {
    return expectedOutputsProvider;
  }

  /**
   * Records actual output for the given test case.
   *
   * @param testCaseId The test case ID
   * @param result The actual result
   */
  public void recordActual(String testCaseId, Object result) {
    // Implementation will be refined in future RT-004
  }
}
