package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.codinglair.taf.sauce.page.abstraction.SauceBasePage;

public class CheckoutPage  extends SauceBasePage {
    private final String PAGE_TITLE_LOCATOR = "";
    private final String FIRST_NAME_INPUT_LOCATOR = "";
    private final String LAST_NAME_INPUT_LOCATOR = "";
    private final String ZIP_INPUT_LOCATOR = "";
    private final String CANCEL_BTN_LOCATOR = "";
    private final String CONTINUE_BTN_LOCATOR = "";

    public CheckoutPage(PlaywrightController testController) {
        super(testController);
    }
}
