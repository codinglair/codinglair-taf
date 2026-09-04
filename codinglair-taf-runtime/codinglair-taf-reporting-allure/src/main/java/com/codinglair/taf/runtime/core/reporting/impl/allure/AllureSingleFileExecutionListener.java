package com.codinglair.taf.runtime.core.reporting.impl.allure;

import org.testng.IExecutionListener;

/** Publishes once after TestNG has finalized all ordinary and Cucumber-orchestrated results. */
public final class AllureSingleFileExecutionListener implements IExecutionListener {
  @Override
  public void onExecutionStart() {
    AllureSingleFileRunCoordinator.executionStarted();
  }

  @Override
  public void onExecutionFinish() {
    AllureSingleFileRunCoordinator.executionFinished();
  }
}
