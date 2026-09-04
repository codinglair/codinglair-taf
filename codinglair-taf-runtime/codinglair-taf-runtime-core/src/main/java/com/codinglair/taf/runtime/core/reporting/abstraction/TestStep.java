package com.codinglair.taf.runtime.core.reporting.abstraction;

/** Represents a step within a test execution. */
public record TestStep(String name, String status, String description) {

  public static TestStep of(String name, String status) {
    return new TestStep(name, status, null);
  }

  public static TestStep of(String name, String status, String description) {
    return new TestStep(name, status, description);
  }
}
