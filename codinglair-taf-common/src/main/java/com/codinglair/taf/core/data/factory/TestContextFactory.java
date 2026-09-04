package com.codinglair.taf.core.data.factory;

import com.codinglair.taf.core.data.abstraction.TestContext;

/** Factory for creating TestContext instances. */
public class TestContextFactory {

  /**
   * Creates a test context from the given class name. This is a placeholder implementation.
   *
   * @param className The class name (unused in this placeholder implementation)
   * @param envProps The environment properties
   * @return A new TestContext with default data providers
   */
  public static TestContext<?, ?> createContext(String className, Object envProps) {
    return new TestContext<>(
        new com.codinglair.taf.core.data.impl.EmptyDataProvider<>(),
        new com.codinglair.taf.core.data.impl.EmptyDataProvider<>());
  }
}
