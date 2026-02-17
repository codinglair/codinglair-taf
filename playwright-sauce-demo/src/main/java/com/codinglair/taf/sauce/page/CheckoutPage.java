package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.sauce.page.abstraction.SauceBasePage;

public class CheckoutPage  extends SauceBasePage {
    private final String PAGE_TITLE_LOCATOR = "span.title";
    private final String FIRST_NAME_INPUT_LOCATOR = "#first-name";
    private final String LAST_NAME_INPUT_LOCATOR = "#last-name";
    private final String ZIP_INPUT_LOCATOR = "#postal-code";
    private final String CANCEL_BTN_LOCATOR = "button#cancel";
    private final String CONTINUE_BTN_LOCATOR = "input#continue";

    public CheckoutPage(PlaywrightController testController) {
        super(testController);
    }

    @Override
    public boolean isActivePage() {
        return testController.getPage().locator(PAGE_TITLE_LOCATOR).textContent()
                .equals("Checkout: Your Information");
    }

    @TafStep("Enter first name {0}")
    public void typeFirstName(String firstName) {
        testController.getPage().locator(FIRST_NAME_INPUT_LOCATOR).fill(firstName);
    }

    @TafStep("Enter last name {0}")
    public void typeLastName(String lastName) {
        testController.getPage().locator(LAST_NAME_INPUT_LOCATOR).fill(lastName);
    }

    @TafStep("Enter zip code {0}")
    public void typeZipCode(String zipCode) {
        testController.getPage().locator(ZIP_INPUT_LOCATOR).fill(zipCode);
    }

    @TafStep("Cancel Check-out")
    public void cancelCheckout() {
        testController.getPage().locator(CANCEL_BTN_LOCATOR).click();
    }

    @TafStep("Continue Checkout")
    public void continueCheckout() {
        testController.getPage().locator(CONTINUE_BTN_LOCATOR).click();
    }
}
