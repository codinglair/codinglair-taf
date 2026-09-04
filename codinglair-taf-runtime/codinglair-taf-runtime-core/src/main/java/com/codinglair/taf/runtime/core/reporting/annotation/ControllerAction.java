package com.codinglair.taf.runtime.core.reporting.annotation;

import java.lang.annotation.*;

/** Marks a meaningful public controller operation. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ControllerAction {
  String value();
}
