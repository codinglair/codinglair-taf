package com.codinglair.taf.demo.sauce.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public record LocatorSpec(String value) {
  public LocatorSpec {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("locator must not be blank");
  }

  Locator resolve(Page page) {
    return page.locator(value);
  }

  Locator resolve(Locator parent) {
    return parent.locator(value);
  }
}
