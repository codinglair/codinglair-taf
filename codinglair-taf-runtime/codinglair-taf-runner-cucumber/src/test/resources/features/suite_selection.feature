Feature: Dynamically selected business suites

  @suite-a @requirement-SUITE-101
  Scenario: Suite A business behavior
    Given an account with a zero balance
    Then the account balance is 0 dollars

  @suite-b @requirement-SUITE-102
  Scenario: Suite B business behavior
    Given an account with a zero balance
    Then the account balance is 0 dollars
