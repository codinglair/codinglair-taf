package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.annotation.reporting.CaptureOutput;
import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.codinglair.taf.sauce.data.ProductPojo;
import com.codinglair.taf.sauce.page.abstraction.SauceBasePage;

public class ShoppingCartPage extends SauceBasePage {
    private final String PAGE_TITLE_LOCATOR = "";
    private final String PRODUCT_NAME_LBL_LOCATOR = "";
    private final String PPRODUCT_DESCR_LBL_LOCATOR = "";
    private final String PRICE_LBL_LOCATOR = "";
    private final String CHECKOUT_BTN_LOCATOR = "";
    private final String CONTINUE_SHOPPING_BTN_LOCATOR = "";
    private final String REMOVE_BTN_LOCATOR = "";

    public ShoppingCartPage(PlaywrightController testController) {
        super(testController);
    }

    public String getProductName() {
        return testController.getPage().locator(PRODUCT_NAME_LBL_LOCATOR).textContent();
    }

    public String getProductDescription() {
        return testController.getPage().locator(PPRODUCT_DESCR_LBL_LOCATOR).textContent();
    }

    public String getProductPrice() {
        return testController.getPage().locator(PRICE_LBL_LOCATOR)
                .textContent()
                .replace("$", "");
    }

    @TafStep("Perform check-out")
    public void clickCheckoutButton() {
        safeClickElement(CHECKOUT_BTN_LOCATOR);
    }

    @TafStep("Remove item from the cart")
    public  void clickRemoveButton() {
        safeClickElement(REMOVE_BTN_LOCATOR);
    }

    @TafStep("Click continue shopping")
    public void clickContinueShoppingButton() {
        safeClickElement(CONTINUE_SHOPPING_BTN_LOCATOR);
    }

    @TafStep("Get Product Details")
    @CaptureOutput
    public ProductPojo getProductDetails() {
        ProductPojo  actualProduct = new ProductPojo();
        //actualProduct.setTestCaseId(testCaseId);
        actualProduct.setProductName(getProductName());
        actualProduct.setProductDescription(getProductDescription());
        actualProduct.setPrice(getProductPrice());
        return actualProduct;
    }

}
