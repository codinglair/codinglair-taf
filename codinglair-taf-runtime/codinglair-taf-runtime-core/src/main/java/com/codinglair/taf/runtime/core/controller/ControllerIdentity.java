package com.codinglair.taf.runtime.core.controller;

import java.util.Objects;

/** Stable typed and named identity for a controller instance. */
public record ControllerIdentity(Class<? extends TestController> type, String name) {
  public ControllerIdentity {
    Objects.requireNonNull(type, "type");
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Controller name must not be blank");
  }
}
