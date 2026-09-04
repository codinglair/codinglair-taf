package com.codinglair.taf.demo.sauce.validation;

import com.codinglair.taf.demo.sauce.model.Product;
import com.codinglair.taf.demo.sauce.model.ProductExpectation;
import com.codinglair.taf.runtime.core.reporting.annotation.Validation;

public class ProductValidator {
  @Validation("Product details match catalog")
  public void validate(ProductExpectation expected, Product actual) {
    if (!expected.productName().equals(actual.productName())
        || !expected.description().equals(actual.description())
        || expected.price().compareTo(actual.price()) != 0) {
      throw new AssertionError("Product mismatch: expected=" + expected + ", actual=" + actual);
    }
  }
}
