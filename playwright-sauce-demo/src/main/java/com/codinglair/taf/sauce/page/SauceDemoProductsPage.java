package com.codinglair.taf.sauce.page;

import com.microsoft.playwright.Locator;
import com.codinglair.taf.core.annotation.reporting.CaptureOutput;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.codinglair.taf.sauce.data.ProductPojo;

import java.util.List;

public class SauceDemoProductsPage extends BasePage<PlaywrightController> {
    private final String PAGE_TITLE_LOCATOR = "//span[@class='title'][@data-test='title']";
    private final String INVENTORY_ITEMS_LOCTOR = "//div[@class='inventory_item']";
    private final String INVENTORY_ITEM_PARTIAL = "//div[text()='%s']/ancestor::div[@class='inventory_item']";
    private final String PRODUCT_NAME_LOCATOR = "div.inventory_item_name";
    private final String PRODUCT_DESC_LOCATOR = "div.inventory_item_desc";
    private final String PRODUCT_PRICE_LOCATOR = "div.inventory_item_price";

    public SauceDemoProductsPage(PlaywrightController testController) {
        super(testController);
    }

    public String getPageTitle(){
        return testController.getPage().locator(PAGE_TITLE_LOCATOR).textContent();
    }

    public List<?> getInventoryItems() {
        return testController.getPage().locator(INVENTORY_ITEMS_LOCTOR).all();
    }

    public Locator getInventoryItem(String inventoryName) {
        return testController.getPage().locator(String.format(INVENTORY_ITEM_PARTIAL, inventoryName));
    }

    public String getProductName(String productName) {
        return getInventoryItem(productName).locator(PRODUCT_NAME_LOCATOR).textContent();
    }

    public String getProductDescription(String productName){
        return getInventoryItem(productName).locator(PRODUCT_DESC_LOCATOR).textContent();
    }

    public String getProductPrice(String productName) {
        return getInventoryItem(productName).locator(PRODUCT_PRICE_LOCATOR)
                .textContent()
                .replace("$", "");
    }

    @CaptureOutput
    public ProductPojo getProductDetails(String productName) {
        ProductPojo actualProduct = new ProductPojo();
        //actualProduct.setTestCaseId(testCaseId);
        actualProduct.setProductName(getProductName(productName));
        actualProduct.setProductDescription(getProductDescription(productName));
        actualProduct.setPrice(getProductPrice(productName));
        return actualProduct;
    }
}
