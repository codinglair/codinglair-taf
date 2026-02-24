package com.codinglair.taf.sauce.page.abstraction;

import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.microsoft.playwright.Locator;

import java.lang.invoke.MethodHandles;

public abstract class SauceBasePage extends BasePage<PlaywrightController> {
    public SauceBasePage(PlaywrightController testController) {
        super(testController);
    }

    public abstract boolean isActivePage();

    protected boolean isElementVisible(String locator) {
        return testController.getPage().locator(locator).isVisible();
    }

    protected boolean isElementVisible(Locator locator) {
        return locator.isVisible();
    }

    protected void safeClickElement(Locator locator) {
        if(!isElementVisible(locator)) {
            String errMsg = String.format("Web element %s is not visible on the page %s to perform a click.",
                    locator, this.getClass().getSimpleName());
            logger.error(errMsg);
            throw new RuntimeException(errMsg);
        }
        locator.click();
    }

    protected void safeClickElement(String locator) {
        if(!isElementVisible(locator)) {
            String errMsg = String.format("Web element %s is not visible on the page %s to perform a click.",
                    locator, this.getClass().getSimpleName());
            logger.error(errMsg);
            throw new RuntimeException(errMsg);
        }
        testController.getPage().locator(locator).click();
    }
}
