@requirement-ACCT-105
Feature: Skipped scenario cleanup

  Scenario: Undefined behavior still releases its session
    Given an account with a zero balance
    When an undefined business action occurs
    Then this step is not reached
