Feature: SauceDemo E2E Purchase Scenario
  As a premium customer
  I want to complete a full purchase cycle
  So that I can receive my items

  @TestCaseId:TC0003 @Acceptance
  Scenario: Successful purchase of a single item
    Given I am logged in with a user account
    And I have cleared my shopping cart
    When I select a product from the list
    And I add the product to the cart
    And I complete the checkout with user details
    Then I should see the order confirmation message "Thank you for your order!"
    And the cart should be empty