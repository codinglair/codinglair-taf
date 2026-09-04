package com.codinglair.taf.runtime.core.failure;

import java.util.Objects;

public record FailureSignature(String value, String algorithm) {
  public FailureSignature {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(algorithm, "algorithm");
    if (!value.matches("failure-signature:v1:[0-9a-f]{64}") || !algorithm.equals("v1")) {
      throw new IllegalArgumentException("Unsupported failure signature");
    }
  }
}
