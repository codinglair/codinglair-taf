package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.annotation.reporting.CaptureOutput;
import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.codinglair.taf.sauce.data.ProductPojo;
import com.codinglair.taf.sauce.page.abstraction.SauceBasePage;
import com.microsoft.playwright.Locator;

import java.util.List;

public class ShoppingCartPage extends SauceBasePage {
    private final String PAGE_TITLE_LOCATOR = "span.title";
    private final String INVENTORY_LIST_LOCATOR = "//div[@class='cart_list']//div[@class='cart_item_label']";
    private final String CART_ITEM_LOCATOR = "//a[contains(., '%s')]/ancestor::div[@class='cart_item_label']";
    private final String PRODUCT_NAME_LBL_LOCATOR = "div.inventory_item_name";
    private final String PPRODUCT_DESCR_LBL_LOCATOR = "div.inventory_item_desc";
    private final String PRICE_LBL_LOCATOR = "div.inventory_item_price";
    private final String CHECKOUT_BTN_LOCATOR = "button#checkout";
    private final String CONTINUE_SHOPPING_BTN_LOCATOR = "button#continue-shopping";
    private final String REMOVE_BTN_LOCATOR = "//button[contains(., 'Remove')]";

    public ShoppingCartPage(PlaywrightController testController) {
        super(testController);
    }

    @Override
    public boolean isActivePage() {
        return testController.getPage().locator(PAGE_TITLE_LOCATOR).textContent().equals("Your Cart");
    }

    public List<?> getInventoryItems() {
        return testController.getPage().locator(INVENTORY_LIST_LOCATOR).all();
    }

    public Locator getInverntoryItem(String productName) {
        return testController.getPage().locator(String.format(CART_ITEM_LOCATOR, productName));
    }

    public String getProductName(String productName) {
        return getInverntoryItem(productName).locator(PRODUCT_NAME_LBL_LOCATOR).textContent();
    }

    public String getProductDescription(String productName) {
        return getInverntoryItem(productName).locator(PPRODUCT_DESCR_LBL_LOCATOR).textContent();
    }

    public String getProductPrice(String productName) {
        return getInverntoryItem(productName).locator(PRICE_LBL_LOCATOR)
                .textContent()
                .replace("$", "");
    }

    @TafStep("Perform check-out")
    public void clickCheckoutButton() {
        safeClickElement(CHECKOUT_BTN_LOCATOR);
    }

    @TafStep("Remove item from the cart")
    public  void removeItem(String productName) {
        safeClickElement(getInverntoryItem(productName).locator(REMOVE_BTN_LOCATOR));
    }

    @TafStep("Click continue shopping")
    public void clickContinueShoppingButton() {
        safeClickElement(CONTINUE_SHOPPING_BTN_LOCATOR);
    }

    @TafStep("Get Product Details")
    @CaptureOutput
    public ProductPojo getProductDetails(String productName) {
        ProductPojo  actualProduct = new ProductPojo();
        //actualProduct.setTestCaseId(testCaseId);
        actualProduct.setProductName(getProductName(productName));
        actualProduct.setProductDescription(getProductDescription(productName));
        actualProduct.setPrice(getProductPrice(productName));
        return actualProduct;
    }

}
