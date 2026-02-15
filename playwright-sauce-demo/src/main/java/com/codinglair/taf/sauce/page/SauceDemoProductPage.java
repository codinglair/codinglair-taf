package com.codinglair.taf.sauce.page;

import com.codinglair.taf.core.annotation.reporting.CaptureOutput;
import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.controller.impl.PlaywrightController;
import com.codinglair.taf.core.test.abstraction.BaseTest;
import com.codinglair.taf.core.ui.abstraction.BasePage;
import com.codinglair.taf.sauce.data.ProductPojo;
import com.codinglair.taf.sauce.page.abstraction.SauceBasePage;
import com.microsoft.playwright.Locator;

public class SauceDemoProductPage extends SauceBasePage {
    private final String BACK_2_PRODUCTS_LNK_LOCATOR = "button#back-to-products";
    private final String PRODUCT_NAME_LBL_LOCATOR = "div.inventory_details_name.large_size";
    private final String PPRODUCT_DESCR_LBL_LOCATOR = "div.inventory_details_desc.large_size";
    private final String PRICE_LBL_LOCATOR = "div.inventory_details_price";
    private final String ADD_2_CART_BTN_LOCATOR = "button#add-to-cart.btn_primary";
    private final String REMOVE_BTN_LOCATOR = "button#remove";
    private final String SHOPPING_CART_LNK_LOCATOR = "a.shopping_cart_link";
    private final String SHOPPING_CART_BDG_LOCATOR = "span.shopping_cart_badge";

    public SauceDemoProductPage(PlaywrightController testController) {
        super(testController);
    }

    public void clickGetToProductsButton() {
        testController.getPage().locator(BACK_2_PRODUCTS_LNK_LOCATOR).click();
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

    @TafStep("Add item to the cart")
    public void clickAddToCartButton(){
        safeClickElement(ADD_2_CART_BTN_LOCATOR);
    }

    @TafStep("Remove item from the cart")
    public void clickRemoveButton() {
        safeClickElement(REMOVE_BTN_LOCATOR);
    }

    @TafStep("Open shopping cart")
    public void clickShoppingCartLink() {
        safeClickElement(SHOPPING_CART_LNK_LOCATOR);
    }

    public String getShoppingCartCount() {
        String count = "";
        if(isElementVisible(SHOPPING_CART_BDG_LOCATOR)) {
            count = testController.getPage().locator(SHOPPING_CART_BDG_LOCATOR).innerText();
        }
        return count;
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
