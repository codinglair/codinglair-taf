package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.codinglair.taf.sauce.page.abstraction.SauceBasePage;

public class CheckoutCompletePage  extends SauceBasePage {
    private final String PAGE_TITLE_LOCATOR = "span.title";
    private final String THANK_U_LBL_LOCATOR = "h2.complete-header";
    private final String BACK_HOME_BTN_LOCATOR = "button#back-to-products";
    public CheckoutCompletePage(PlaywrightController testController) {
        super(testController);
    }

    @Override
    public boolean isActivePage() {
        return testController.getPage().locator(PAGE_TITLE_LOCATOR).textContent()
                .equals("Checkout: Complete!");
    }

    @TafStep("Retrieve checkout result")
    public String checkoutResult() {
        return testController.getPage().locator(THANK_U_LBL_LOCATOR).textContent();
        //"Thank you for your order!"
    }

    @TafStep("Return back to Home page")
    public void clickBackHome() {
        safeClickElement(BACK_HOME_BTN_LOCATOR);
    }
}
