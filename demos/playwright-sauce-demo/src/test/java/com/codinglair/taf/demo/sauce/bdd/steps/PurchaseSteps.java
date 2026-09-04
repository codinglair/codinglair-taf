package com.codinglair.taf.demo.sauce.bdd.steps;

import com.codinglair.taf.demo.sauce.component.HeaderComponent;
import com.codinglair.taf.demo.sauce.configuration.SauceDemoProperties;
import com.codinglair.taf.demo.sauce.model.LoginInput;
import com.codinglair.taf.demo.sauce.model.ProductExpectation;
import com.codinglair.taf.demo.sauce.page.CartPage;
import com.codinglair.taf.demo.sauce.page.CheckoutPage;
import com.codinglair.taf.demo.sauce.page.ProductsPage;
import com.codinglair.taf.demo.sauce.service.LoginWorkflow;
import com.codinglair.taf.demo.sauce.validation.StringValidator;
import com.codinglair.taf.runtime.core.reporting.annotation.BddStep;
import com.codinglair.taf.runtime.cucumber.CucumberScenarioSession;
import com.codinglair.taf.runtime.definition.TestDefinition;
import com.codinglair.taf.runtime.definition.TestDefinitionResolver;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class PurchaseSteps {
  private final LoginWorkflow login;
  private final ProductsPage products;
  private final HeaderComponent header;
  private final CartPage cart;
  private final CheckoutPage checkout;
  private final StringValidator validator;
  private final SauceDemoProperties properties;
  private final CucumberScenarioSession scenario;
  private final TestDefinitionResolver definitions;
  private LoginInput input;
  private ProductExpectation expected;

  public PurchaseSteps(
      LoginWorkflow login,
      ProductsPage products,
      HeaderComponent header,
      CartPage cart,
      CheckoutPage checkout,
      StringValidator validator,
      SauceDemoProperties properties,
      CucumberScenarioSession scenario,
      TestDefinitionResolver definitions) {
    this.login = login;
    this.products = products;
    this.header = header;
    this.cart = cart;
    this.checkout = checkout;
    this.validator = validator;
    this.properties = properties;
    this.scenario = scenario;
    this.definitions = definitions;
  }

  @Given("a standard shopper is authenticated")
  @BddStep("Authenticate standard shopper")
  public void authenticatedShopper() {
    TestDefinition<LoginInput, ProductExpectation> definition =
        scenario.testDefinition(definitions, LoginInput.class, ProductExpectation.class);
    input = definition.input();
    expected = definition.expectedOutput();
    login.authenticate(properties.baseUrl(), input.username(), input.passwordReference());
  }

  @When("the shopper purchases the configured product")
  @BddStep("Purchase configured product")
  public void purchaseProduct() {
    products.addToCart(input.productName());
    header.openCart();
    cart.checkout();
    checkout.enterInformation(input.firstName(), input.lastName(), input.postalCode());
    checkout.finish();
  }

  @Then("the order is confirmed")
  @BddStep("Verify purchase confirmation")
  public void orderConfirmed() {
    validator.validate(expected.productName(), checkout.confirmation());
  }
}
