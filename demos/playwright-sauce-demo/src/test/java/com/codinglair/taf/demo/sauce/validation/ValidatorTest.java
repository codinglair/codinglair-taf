package com.codinglair.taf.demo.sauce.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.demo.sauce.model.Product;
import com.codinglair.taf.demo.sauce.model.ProductExpectation;
import java.math.BigDecimal;

class ValidatorTest {
  private final ProductValidator products = new ProductValidator();
  private final StringValidator strings = new StringValidator();

  @org.junit.jupiter.api.Test
  void matchingProductPasses() {
    ProductExpectation expected =
        new ProductExpectation("Backpack", "Laptop backpack", new BigDecimal("29.99"));
    Product actual = new Product("Backpack", "Laptop backpack", new BigDecimal("29.990"));

    assertDoesNotThrow(() -> products.validate(expected, actual));
  }

  @org.junit.jupiter.api.Test
  void productMismatchIncludesExpectedAndActualDiagnostics() {
    ProductExpectation expected =
        new ProductExpectation("Backpack", "Expected", new BigDecimal("29.99"));
    Product actual = new Product("Backpack", "Actual", new BigDecimal("29.99"));

    assertThrows(AssertionError.class, () -> products.validate(expected, actual));
  }

  @org.junit.jupiter.api.Test
  void stringMismatchFails() {
    assertThrows(AssertionError.class, () -> strings.validate("Products", "Login"));
  }
}
