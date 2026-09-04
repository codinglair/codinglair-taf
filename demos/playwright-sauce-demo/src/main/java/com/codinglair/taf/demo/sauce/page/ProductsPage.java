package com.codinglair.taf.demo.sauce.page;

import com.codinglair.taf.demo.sauce.model.Product;
import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Locator.FilterOptions;
import com.microsoft.playwright.Page;
import java.math.BigDecimal;

public class ProductsPage {
  private static final LocatorSpec TITLE = new LocatorSpec("[data-test='title']");
  private static final LocatorSpec PRODUCT_ITEMS = new LocatorSpec("[data-test='inventory-item']");
  private static final LocatorSpec PRODUCT_NAME =
      new LocatorSpec("[data-test='inventory-item-name']");
  private static final LocatorSpec PRODUCT_DESCRIPTION =
      new LocatorSpec("[data-test='inventory-item-desc']");
  private static final LocatorSpec PRODUCT_PRICE =
      new LocatorSpec("[data-test='inventory-item-price']");
  private static final LocatorSpec ADD_TO_CART =
      new LocatorSpec("button[data-test^='add-to-cart']");
  private final PlaywrightObjectFactory factory;

  public ProductsPage(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  @PageAction("Read products title")
  public String title() {
    return titleLocator().textContent();
  }

  @PageAction("Read product details")
  public Product product(String productName) {
    return factory.page("shop", ProductsView::new).product(productName);
  }

  @PageAction("Add product to cart")
  public void addToCart(String productName) {
    factory.page("shop", ProductsView::new).addToCart(productName);
  }

  private Locator titleLocator() {
    return factory.page("shop", ProductsPage::createTitleLocator);
  }

  private static Locator createTitleLocator(Page page) {
    return TITLE.resolve(page);
  }

  private static final class ProductsView {
    private final Page page;

    private ProductsView(Page page) {
      this.page = page;
    }

    private Product product(String productName) {
      Locator item = item(productName);
      return new Product(
          PRODUCT_NAME.resolve(item).textContent(),
          PRODUCT_DESCRIPTION.resolve(item).textContent(),
          new BigDecimal(PRODUCT_PRICE.resolve(item).textContent().replace("$", "")));
    }

    private void addToCart(String productName) {
      ADD_TO_CART.resolve(item(productName)).click();
    }

    private Locator item(String productName) {
      return PRODUCT_ITEMS.resolve(page).filter(new FilterOptions().setHasText(productName));
    }
  }
}
