package com.codinglair.taf.runtime.environment.spring;

/** Checks whether an opaque secret alias is resolvable without reading its value. */
@FunctionalInterface
public interface SecretReferenceAvailability {
  boolean isAvailable(String reference);
}
