package com.codinglair.taf.qualification;

import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
import io.cucumber.java.en.Then;

public final class BusinessEvidenceSteps {
  private final CucumberScenarioSession scenario;

  public BusinessEvidenceSteps(CucumberScenarioSession scenario) { this.scenario = scenario; }

  @Then("the active scenario retains a business diagnostic")
  public void retainDiagnostic() {
    scenario.session().getArtifactCollector().addArtifact("business-diagnostic", "diagnostic",
        "audience=business\nscenario=readable\nsecret=[REDACTED]", "text/plain", "Business diagnostic");
  }

  @Then("the expected business failure is raised")
  public void expectedFailure() { throw new AssertionError("QE-132-002 expected business failure marker"); }
}
