package com.codinglair.taf.sauce.bdd.steps;

import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.data.abstraction.TestContext;
import com.codinglair.taf.core.test.TestLifecycleContainer;
import com.codinglair.taf.core.validation.impl.StringValidator;
import com.codinglair.taf.sauce.data.ProductPojo;
import com.codinglair.taf.sauce.data.WebUser;
import com.codinglair.taf.sauce.page.*;
import com.codinglair.taf.sauce.service.LoginService;
import com.codinglair.taf.sauce.validation.ProductValidator;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.assertj.core.api.Assertions;

import java.util.List;


public class SaucePurchaseSteps {

    private WebUser getCurrentUser() {
        //return context().getTestInput(BaseTest.getActiveTestCaseId());
        return context().getTestInput(TestLifecycleContainer.getManager().getActiveTestCaseId());
    }

    private ProductPojo getCurrentProduct() {
        return context().getExpectedTestOutput(TestLifecycleContainer.getManager().getActiveTestCaseId());
    }

    private List<ProductPojo> getCurrentProducts() {
        return context().getExpectedTestOutputs(TestLifecycleContainer.getManager().getActiveTestCaseId());
    }

    // Using the Thread-Local accessors from BaseTest
    private PlaywrightController controller() {
        return TestLifecycleContainer.getManager().getController(); }
    private TestContext<WebUser, ProductPojo> context() {
        return (TestContext<WebUser, ProductPojo>) TestLifecycleContainer.getManager().getContext();
    }

    @Given("I am logged in with a user account")
    public void loginAsUser() {
        // We use the correlationId logic we built earlier!
        WebUser user = getCurrentUser();
        LoginService.login(controller(), context().getEnvironmentProperties(), user);
    }

    @When("I add the product to the cart")
    public void addToCart() {
        ProductPojo product = getCurrentProduct();

        SauceDemoProductPage sauceDemoProductPage = new SauceDemoProductPage(controller());
        StringValidator validator = new StringValidator();
        validator.validate(product.getProductName(), sauceDemoProductPage.getProductName());
        sauceDemoProductPage.clickAddToCartButton();
        validator.validate("1", sauceDemoProductPage.getShoppingCartCount());
        sauceDemoProductPage.clickShoppingCartLink();

        ShoppingCartPage cart = new ShoppingCartPage(controller());
        cart.getProductDetails(product.getProductName());
        new ProductValidator().validate(product, cart.getProductDetails(product.getProductName()));
    }

    @When("I complete the checkout with user details")
    public void completeCheckout() {
        WebUser user = getCurrentUser();
        ShoppingCartPage shoppingCartPage = new ShoppingCartPage(controller());
        if(shoppingCartPage.isActivePage()) {
            shoppingCartPage.clickCheckoutButton();
        }
        CheckoutPage checkoutPage = new CheckoutPage(controller());
        checkoutPage.typeFirstName(user.getFirstName());
        checkoutPage.typeLastName(user.getLastName());
        checkoutPage.typeZipCode(user.getZipCode());
        checkoutPage.continueCheckout();

        ProductPojo expectedProduct = getCurrentProduct();
        CheckoutOverviewPage overviewPage = new CheckoutOverviewPage(controller());
        new ProductValidator().validate(expectedProduct,
                overviewPage.getProductDetails(expectedProduct.getProductName()));
        overviewPage.finishCheckout();
    }

    @Then("I should see the order confirmation message {string}")
    public void verifyConfirmation(String expectedMessage) {
        CheckoutCompletePage page = new CheckoutCompletePage(controller());
        String actual = page.checkoutResult();
        new StringValidator().validate(expectedMessage, actual);
        page.clickBackHome();
    }

    @Given("I have cleared my shopping cart")
    public void iHaveClearedMyShoppingCart() {
        new SauceDemoProductsPage(controller()).cleanUpCart();
    }

    @When("I select a product from the list")
    public void iSelectProducts() {
        ProductPojo product = getCurrentProduct();
        SauceDemoProductsPage page = new SauceDemoProductsPage(controller());
        page.selectTheProduct(product.getProductName());
        new ProductValidator().validate(product, new SauceDemoProductPage(controller()).getProductDetails());
        //products.forEach(p -> page.selectTheProduct(p.getProductName()));
    }

    @Then("the cart should be empty")
    public void theCartShouldBeEmpty() {
        SauceDemoProductsPage page = new SauceDemoProductsPage(controller());
        Assertions.assertThat(page.isShoppingCartEmpty());
    }
}
