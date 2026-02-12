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
        WebUser user = (WebUser) getContext().getTestInput(BaseTest.getActiveTestCaseId());
        reporter.logStep("Navigate to SauceDemo Login Page");
        controller.navigate(envProps.getEnvProperty("base_url"));
        SauceDemoLoginPage loginPage= new SauceDemoLoginPage(controller);
        //reporter.logStep("Login with standard credentials");
        loginPage.typeUserName(user.getUserName());
        loginPage.typeUserPwd(user.getPwd());
        loginPage.clickLoginBtn();

        reporter.logStep("Verify landing on Products page");
        SauceDemoProductsPage productsPage = new SauceDemoProductsPage(controller);
        new StringValidator().validate("Products", productsPage.getPageTitle());
    }

    @Test
    @TestCaseId("TC0001")
    @TafDescription("Verify product details match the catalog")
    public void testProductDetails() {
        WebUser user = (WebUser) getContext().getTestInput(BaseTest.getActiveTestCaseId());
        ProductPojo expected = (ProductPojo) getContext()
                .getExpectedTestOutput(BaseTest.getActiveTestCaseId());

        // 2. Act: Navigate and perform UI actions
        LoginService.login(controller, envProps, user);

        // 3. Capture: One line retrieves and AUTOMATICALLY stores the actual data
        SauceDemoProductsPage productsPage = new SauceDemoProductsPage(controller);
        productsPage.getProductDetails(expected.getProductName());
        //System.out.print(productsPage.getProductDetails(BaseTest.getActiveTestCaseId(),
        //        expected.getProductName()).toString());

        // 4. Assert: Validator compares what was expected vs what was captured
        new ProductValidator().validate(expected,
                (ProductPojo) context.getLastActualTestOutput(BaseTest.getActiveTestCaseId()));
        //validator.validate(expected, context.getLastActualOutput());
    }
}


