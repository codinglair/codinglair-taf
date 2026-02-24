package com.codinglair.taf.sauce.tests.functional;

import com.codinglair.taf.core.annotation.data.TestCaseId;
import com.codinglair.taf.core.annotation.reporting.TafDescription;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.test.abstraction.BaseTest;
import com.codinglair.taf.core.validation.impl.StringValidator;
import com.codinglair.taf.sauce.data.ProductPojo;
import com.codinglair.taf.sauce.page.SauceDemoLoginPage;
import com.codinglair.taf.sauce.page.SauceDemoProductsPage;
import com.codinglair.taf.sauce.data.WebUser;

import com.codinglair.taf.sauce.service.LoginService;
import com.codinglair.taf.sauce.validation.ProductValidator;
import org.testng.annotations.Test;

public class SauceDemoLoginTest extends BaseTest<PlaywrightController,
        WebUser,
        ProductPojo> {
    @Test
    @TestCaseId("TC0001")
    @TafDescription("Verifies that a standard user can login successfully to SauceDemo")
    public void testSuccessfulLogin() {
        WebUser user = (WebUser) getContext().getTestInput(getActiveTestCaseId());
        getReporter().logStep("Navigate to SauceDemo Login Page");
        getController().navigate(getEnvProps().getEnvProperty("base_url"));
        SauceDemoLoginPage loginPage= new SauceDemoLoginPage(getController());
        //reporter.logStep("Login with standard credentials");
        loginPage.typeUserName(user.getUserName());
        loginPage.typeUserPwd(user.getPwd());
        loginPage.clickLoginBtn();

        getReporter().logStep("Verify landing on Products page");
        SauceDemoProductsPage productsPage = new SauceDemoProductsPage(getController());
        new StringValidator().validate("Products", productsPage.getPageTitle());
    }

    @Test
    @TestCaseId("TC0002")
    @TafDescription("Verify product details match the catalog")
    public void testProductDetails() {
        WebUser user = (WebUser) getContext().getTestInput(getActiveTestCaseId());
        ProductPojo expected = (ProductPojo) getContext()
                .getExpectedTestOutput(getActiveTestCaseId());

        // 2. Act: Navigate and perform UI actions
        LoginService.login(getController(), getEnvProps(), user);

        // 3. Capture: One line retrieves and AUTOMATICALLY stores the actual data
        SauceDemoProductsPage productsPage = new SauceDemoProductsPage(getController());
        productsPage.cleanUpCart();
        productsPage.getProductDetails(expected.getProductName());
        // 4. Assert: Validator compares what was expected vs what was captured
        new ProductValidator().validate(expected,
                (ProductPojo) getContext().getLastActualTestOutput(getActiveTestCaseId()));
    }
}


