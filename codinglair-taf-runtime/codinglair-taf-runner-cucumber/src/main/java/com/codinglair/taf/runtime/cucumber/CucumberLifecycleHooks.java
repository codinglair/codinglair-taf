package com.codinglair.taf.runtime.cucumber;

import io.cucumber.java.Scenario;

/**
 * Compatibility facade for the former hook name.
 *
 * @deprecated register {@link TafCucumberHooks} as framework glue. This facade intentionally has no
 *     Cucumber hook annotations so it cannot become a second lifecycle owner.
 */
@Deprecated(forRemoval = true)
public final class CucumberLifecycleHooks {
  private final TafCucumberHooks delegate;

  public CucumberLifecycleHooks(CucumberScenarioSession scenarioSession) {
    this.delegate = new TafCucumberHooks(scenarioSession);
  }

  public void openSession(Scenario scenario) {
    delegate.openSession(scenario);
  }

  public void closeSession() {
    delegate.closeSession();
  }
}
