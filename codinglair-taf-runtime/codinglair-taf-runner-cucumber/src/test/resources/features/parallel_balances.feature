@requirement-ACCT-103
Feature: Parallel account balances

  Scenario Outline: Each customer has an isolated balance
    Given an account with a zero balance
    When the customer deposits <amount> dollars
    Then the account balance is <amount> dollars

    Examples:
      | amount |
      | 1      |
      | 2      |
      | 3      |
      | 4      |
      | 5      |
      | 6      |
      | 7      |
      | 8      |
