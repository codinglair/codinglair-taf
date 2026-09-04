package com.codinglair.taf.runtime.core.reporting.annotation;

import java.lang.annotation.*;

/** Marks a consumer validation boundary. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Validation {
  String value();
}
