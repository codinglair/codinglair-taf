package com.codinglair.taf.demo.sauce.component;

import com.codinglair.taf.demo.sauce.page.LocatorSpec;
import com.codinglair.taf.runtime.core.reporting.annotation.ComponentAction;
import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/** PCOM is present because the header is reusable across multiple pages. */
public class HeaderComponent {
  private static final LocatorSpec ROOT = new LocatorSpec("[data-test='primary-header']");
  private static final LocatorSpec CART = new LocatorSpec("[data-test='shopping-cart-link']");
  private final PlaywrightObjectFactory factory;

  public HeaderComponent(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  @ComponentAction("Read header")
  public String text() {
    return factory.component("shop", ROOT.value(), HeaderComponent::root).textContent();
  }

  @ComponentAction("Open cart")
  public void openCart() {
    factory.component("shop", CART.value(), HeaderComponent::root).click();
  }

  private static Locator root(Page page, Locator root) {
    return root;
  }
}
