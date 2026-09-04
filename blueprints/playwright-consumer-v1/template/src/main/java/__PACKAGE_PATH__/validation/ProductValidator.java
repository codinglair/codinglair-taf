package __BASE_PACKAGE__.validation;

import com.codinglair.taf.runtime.core.reporting.annotation.Validation;

public class ProductValidator {
  @Validation("Product title matches")
  public void matches(String expected, String actual) {
    if (!expected.equals(actual)) throw new AssertionError("Unexpected product title");
  }
}
