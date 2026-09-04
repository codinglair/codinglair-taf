package com.codinglair.taf.runtime.core.reporting.abstraction;

/** Represents a test execution unit. */
public record TafTest(String name, String className, String stackTrace) {

  public static TafTest of(String name, String className) {
    return new TafTest(name, className, null);
  }

  public static TafTest of(String name, String className, String stackTrace) {
    return new TafTest(name, className, stackTrace);
  }
}
