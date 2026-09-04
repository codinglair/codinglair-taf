package com.codinglair.taf.runtime.cucumber;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/** Framework-owned sole lifecycle owner for each Cucumber scenario. */
public final class TafCucumberHooks {
  private final CucumberScenarioSession scenarioSession;

  public TafCucumberHooks(CucumberScenarioSession scenarioSession) {
    this.scenarioSession = scenarioSession;
  }

  @Before(order = Integer.MIN_VALUE)
  public void openSession(Scenario scenario) {
    scenarioSession.start(scenario);
  }

  @After(order = Integer.MIN_VALUE)
  public void closeSession() {
    scenarioSession.close();
  }
}
