package com.codinglair.taf.contracts;

import java.util.Objects;

/** Safe scaffold request; adapters return assets and never write to disk. */
public record ContractScaffoldRequest(
    ContractDocument document, String packageName, String className) {
  public ContractScaffoldRequest {
    document = Objects.requireNonNull(document, "document must not be null");
    if (packageName == null || !packageName.matches("[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)*")) {
      throw new IllegalArgumentException("packageName must be a valid Java package");
    }
    if (className == null || !className.matches("[A-Z_$][\\w$]*")) {
      throw new IllegalArgumentException("className must be a valid Java type name");
    }
  }
}
