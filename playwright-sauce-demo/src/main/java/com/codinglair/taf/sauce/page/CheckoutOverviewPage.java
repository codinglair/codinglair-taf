package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.annotation.reporting.CaptureOutput;
import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.codinglair.taf.sauce.data.ProductPojo;
import com.codinglair.taf.sauce.page.abstraction.SauceBasePage;
import com.microsoft.playwright.Locator;

import java.util.List;
import java.util.Optional;

public class CheckoutOverviewPage  extends SauceBasePage {
    private final String PAGE_TITLE_LOCATOR = "span.title";
    private final String INVENTORY_LIST_LOCATOR = "//div[@class='cart_list']//div[@class='cart_item_label']";
    private final String CART_ITEM_LOCATOR = "//a[contains(., '%s')]/ancestor::div[@class='cart_item_label']";
    private final String PRODUCT_NAME_LBL_LOCATOR = "div.inventory_item_name";
    private final String PPRODUCT_DESCR_LBL_LOCATOR = "div.inventory_item_desc";
    private final String PRICE_LBL_LOCATOR = "div.inventory_item_price";
    private final String ITEM_TOTAL_LBL_LOCATOR = "div.summary_subtotal_label";
    private final String TAX_LBL_LOCATOR = "div.summary_tax_label";
    private final String TOTAL_LBL_LOCATOR = "div.summary_total_label";
    private final String CANCEL_BTN_LOCATOR = "button#cancel";
    private final String FINISH_BTN_LOCATOR = "button#finish";

    public CheckoutOverviewPage(PlaywrightController testController) {
        super(testController);
    }

    @Override
    public boolean isActivePage() {
        return testController.getPage().locator(PAGE_TITLE_LOCATOR).textContent()
                .equals("Checkout: Overview");
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

    public String getSubTotalAmount() {
        String subTotalPlaceholder =
                testController.getPage().locator(ITEM_TOTAL_LBL_LOCATOR).textContent();
        return Optional.ofNullable(subTotalPlaceholder)
                .filter(str -> !str.isBlank())
                .filter(str -> str.contains(":"))
                .map(str -> str.split(":")[1]
                            .trim()
                            .replace("$", ""))
                .orElseThrow(() -> new RuntimeException(String
                        .format("Failed to retrieve subtotal price from %s locator on %s page",
                                ITEM_TOTAL_LBL_LOCATOR, this.getClass().getSimpleName())));
    }

    public String getTax() {
        return Optional.ofNullable(testController.getPage().locator(TAX_LBL_LOCATOR).textContent())
                .filter(str -> !str.isBlank())
                .filter(str -> str.contains(":"))
                .map(str -> str.split(":")[1]
                        .trim()
                        .replace("$", ""))
                .orElseThrow(() -> new RuntimeException(String.format("Failed to retrieve tax from %s locator on %s page",
                        TAX_LBL_LOCATOR, this.getClass().getSimpleName())));
    }

    public String getTotal() {
        return Optional.ofNullable(testController.getPage().locator(TOTAL_LBL_LOCATOR).textContent())
                .filter(str -> !str.isBlank())
                .filter(str -> str.contains(":"))
                .map(str -> str.split(":")[1]
                        .trim()
                        .replace("$", ""))
                .orElseThrow(() -> new RuntimeException(String.format("Failed to retrieve total amount from %s locator on %s page",
                        TOTAL_LBL_LOCATOR, this.getClass().getSimpleName())));
    }

    @TafStep("Cancel checkout")
    public void cancelCheckout(){
        safeClickElement(CANCEL_BTN_LOCATOR);
    }

    @TafStep("Finish checkout")
    public void finishCheckout() {
        safeClickElement(FINISH_BTN_LOCATOR);
    }

    @TafStep("Review Product Details at checkout")
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