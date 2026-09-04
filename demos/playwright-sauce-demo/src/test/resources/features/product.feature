@test-case-TC0003
Feature: Purchase a SauceDemo product
  Scenario: A standard shopper completes a single-item purchase
    Given a standard shopper is authenticated
    When the shopper purchases the configured product
    Then the order is confirmed
