package com.codinglair.taf.runtime.core.reporting.impl.allure.support;

import io.cucumber.java.en.Given;

public final class Rep005Steps {
  @Given("a finalized sanitized Cucumber result")
  public void finalizedResult() {
    // The terminal runner event, rather than consumer glue, owns publication.
  }
}
