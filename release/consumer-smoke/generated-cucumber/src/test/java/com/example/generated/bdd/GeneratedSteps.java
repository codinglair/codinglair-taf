package com.example.generated.bdd;

import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
import io.cucumber.java.en.Then;

public final class GeneratedSteps {
  private final CucumberScenarioSession scenarioSession;

  public GeneratedSteps(CucumberScenarioSession scenarioSession) {
    this.scenarioSession = scenarioSession;
  }

  @Then("the generated project is ready")
  public void generatedProjectIsReady() {
    if (scenarioSession.session() == null) {
      throw new AssertionError("TafCucumberHooks did not open the scenario TestSession");
    }
  }
}

