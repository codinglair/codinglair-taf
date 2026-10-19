package com.codinglair.taf.runtime.core.lifecycle;

/** Stable failure raised when capability work is attempted outside its owning invocation. */
public final class LifecycleViolationException extends IllegalStateException {
  public static final String ERROR_CODE = "TAF-LIFECYCLE-001";

  public LifecycleViolationException(String detail) {
    super(ERROR_CODE + ": " + detail);
  }
}
