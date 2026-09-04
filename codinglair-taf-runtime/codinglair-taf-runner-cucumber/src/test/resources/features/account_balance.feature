@requirement-ACCT-101 @test-case-ACCT-TC-101
Feature: Account balance
  Customers need a clear balance after a deposit.

  Scenario: Deposited funds are reflected in the balance
    Given an account with a zero balance
    When the customer deposits 25 dollars
    Then the account balance is 25 dollars
