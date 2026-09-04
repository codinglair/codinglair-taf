package com.codinglair.taf.runtime.environment;

import java.util.function.Supplier;

/** Framework-neutral target compatible with Spring's dynamic-property registration model. */
@FunctionalInterface
public interface DynamicPropertySink {
  void add(String name, Supplier<Object> valueSupplier);
}
