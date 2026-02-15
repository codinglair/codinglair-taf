package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;

public class CheckoutCompletePage  extends BasePage<PlaywrightController> {
    private final String PAGE_TITLE_LOCATOR = "";
    private final String THANK_U_LBL_LOCATOR = "";
    private final String BACK_HOME_BTN_LOCATOR = "";
    public CheckoutCompletePage(PlaywrightController testController) {
        super(testController);
    }
}
