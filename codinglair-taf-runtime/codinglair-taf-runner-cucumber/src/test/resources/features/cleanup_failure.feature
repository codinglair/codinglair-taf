@requirement-ACCT-104
Feature: Cleanup failure visibility

  Scenario: Cleanup failures fail the business scenario
    Given an account whose cleanup fails
    Then the account balance is 0 dollars
