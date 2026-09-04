package com.codinglair.taf.runtime.cucumber.glue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.concurrent.atomic.AtomicInteger;

public final class AccountSteps {
  public static final AtomicInteger CLEANUPS = new AtomicInteger();
  public static final java.util.Set<String> TEST_CASE_IDS =
      java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final CucumberScenarioSession scenarioSession;
  private int balance;

  public AccountSteps(CucumberScenarioSession scenarioSession) {
    this.scenarioSession = scenarioSession;
  }

  @Before(value = "@hook-failure", order = 0)
  public void failAfterFrameworkSessionStarts() {
    scenarioSession.session().addCleanupListener(ignored -> CLEANUPS.incrementAndGet());
    throw new IllegalStateException("consumer hook failed");
  }

  @Given("an account with a zero balance")
  public void zeroBalance() {
    scenarioSession.session().addCleanupListener(ignored -> CLEANUPS.incrementAndGet());
    scenarioSession.testCaseId().ifPresent(TEST_CASE_IDS::add);
    balance = 0;
  }

  @Given("an account whose cleanup fails")
  public void cleanupFails() {
    scenarioSession
        .session()
        .addCleanupListener(
            ignored -> {
              CLEANUPS.incrementAndGet();
              throw new IllegalStateException("cleanup failed");
            });
    balance = 0;
  }

  @When("the customer deposits {int} dollars")
  public void deposit(int amount) {
    balance += amount;
    scenarioSession.attach("authorization=business-secret", "text/plain", "deposit receipt");
  }

  @Then("the account balance is {int} dollars")
  public void balanceIs(int expected) {
    assertEquals(expected, balance);
  }

  @When("the deposit operation fails")
  public void failedDeposit() {
    fail("declined by the business service");
  }

  @Then("this step is not reached")
  public void notReached() {
    fail("skipped step executed unexpectedly");
  }
}
