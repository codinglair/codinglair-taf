package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.sauce.page.abstraction.SauceBasePage;
import com.microsoft.playwright.Locator;
import com.codinglair.taf.core.annotation.reporting.CaptureOutput;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.codinglair.taf.sauce.data.ProductPojo;

import java.util.List;

public class SauceDemoProductsPage extends SauceBasePage {
    private final String PAGE_TITLE_LOCATOR = "//span[@class='title'][@data-test='title']";
    private final String INVENTORY_ITEMS_LOCTOR = "//div[@class='inventory_item']";
    private final String INVENTORY_ITEM_PARTIAL = "//div[text()='%s']/ancestor::div[@class='inventory_item']";
    private final String PRODUCT_NAME_LOCATOR = "div.inventory_item_name";
    private final String PRODUCT_DESC_LOCATOR = "div.inventory_item_desc";
    private final String PRODUCT_PRICE_LOCATOR = "div.inventory_item_price";
    private final String REMOVE_BUTTONS_LOCATOR = "//div[@class='pricebar']/button[contains(., 'Remove')]";



    public SauceDemoProductsPage(PlaywrightController testController) {
        super(testController);
    }

    @Override
    public boolean isActivePage() {
        return testController.getPage().locator(PAGE_TITLE_LOCATOR).textContent().equals("Products");
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

    @TafStep("Clean up the cart")
    public void cleanUpCart(){
        List<Locator> allRemoveButtons =
                testController.getPage().locator(REMOVE_BUTTONS_LOCATOR).all();
        allRemoveButtons.forEach(l -> {
            if(l.isVisible()) l.click(); ;
        });
    }

    @TafStep("Select the product")
    public void selectTheProduct(String productName) {
        safeClickElement(getInventoryItem(productName).locator(PRODUCT_NAME_LOCATOR));
    }

    @TafStep("Get Product Details")
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
