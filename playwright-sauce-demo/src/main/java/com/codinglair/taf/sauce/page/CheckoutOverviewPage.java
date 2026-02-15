package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;

public class CheckoutOverviewPage  extends BasePage<PlaywrightController> {
    private final String PAGE_TITLE_LOCATOR = "";
    private final String PRODUCT_NAME_LBL_LOCATOR = "";
    private final String PPRODUCT_DESCR_LBL_LOCATOR = "";
    private final String PRICE_LBL_LOCATOR = "";
    private final String ITEM_TOTAL_LBL_LOCATOR = "";
    private final String TAX_LBL_LOCATOR = "";
    private final String TOTAL_LBL_LOCATOR = "";
    private final String CANCEL_BTN_LOCATOR = "";
    private final String FINISH_BTN_LOCATOR = "";

    public CheckoutOverviewPage(PlaywrightController testController) {
        super(testController);
    }
}
