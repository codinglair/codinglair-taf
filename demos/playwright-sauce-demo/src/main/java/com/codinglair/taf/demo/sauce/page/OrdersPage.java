package com.codinglair.taf.demo.sauce.page;

import com.codinglair.taf.web.playwright.PlaywrightObjectFactory;
import com.microsoft.playwright.Page;

/** Example application-owned page object; adapt locators to the system under test. */
public final class OrdersPage {
  private static final LocatorSpec ORDER_ID = new LocatorSpec("[data-test='order-id']");
  private static final LocatorSpec SUBMIT = new LocatorSpec("[data-test='submit-order']");
  private final PlaywrightObjectFactory factory;

  public OrdersPage(PlaywrightObjectFactory factory) {
    this.factory = factory;
  }

  public String submitAndReadOrderId(String baseUrl) {
    return factory.page("shop", page -> submit(page, baseUrl));
  }

  private static String submit(Page page, String baseUrl) {
    page.navigate(baseUrl + "/orders/new");
    SUBMIT.resolve(page).click();
    ORDER_ID.resolve(page).waitFor();
    return ORDER_ID.resolve(page).textContent();
  }
}
