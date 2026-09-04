@requirement-ACCT-102
Feature: Failed business operation cleanup

  Scenario: A declined deposit remains visible
    Given an account with a zero balance
    When the deposit operation fails
    Then this step is not reached
