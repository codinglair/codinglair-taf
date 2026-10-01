package com.codinglair.taf.mobile.appium.platform;

import io.appium.java_client.AppiumBy;
import java.util.Objects;
import org.openqa.selenium.By;

/** Immutable selector specification; contains no driver or element state. */
public record AppleLocator(Kind kind, String value) {
  public enum Kind {
    ACCESSIBILITY,
    PREDICATE,
    CLASS_CHAIN,
    XPATH,
    CSS
  }

  public AppleLocator {
    Objects.requireNonNull(kind);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Locator is required");
  }

  public By by() {
    return switch (kind) {
      case ACCESSIBILITY -> AppiumBy.accessibilityId(value);
      case PREDICATE -> AppiumBy.iOSNsPredicateString(value);
      case CLASS_CHAIN -> AppiumBy.iOSClassChain(value);
      case XPATH -> By.xpath(value);
      case CSS -> By.cssSelector(value);
    };
  }
}
