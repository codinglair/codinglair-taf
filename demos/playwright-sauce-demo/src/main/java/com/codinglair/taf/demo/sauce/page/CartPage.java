package com.codinglair.taf.demo.sauce.page;

import com.codinglair.taf.runtime.core.reporting.annotation.PageAction;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class CartPage {
  private static final LocatorSpec CHECKOUT = new LocatorSpec("[data-test='checkout']");
  private final PlaywrightObjectFactory factory;

  public CartPage(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  @PageAction("Begin checkout")
  public void checkout() {
    factory.page("shop", CartView::new).checkout();
  }

  private static final class CartView {
    private final Locator checkout;

    private CartView(Page page) {
      checkout = CHECKOUT.resolve(page);
    }

    private void checkout() {
      checkout.click();
    }
  }
}
