package com.codinglair.taf.contracts;

/** Raised when a concrete controller exchange violates a validated contract. */
public final class ContractViolationException extends AssertionError {
  public ContractViolationException(String message) {
    super(message);
  }
}
