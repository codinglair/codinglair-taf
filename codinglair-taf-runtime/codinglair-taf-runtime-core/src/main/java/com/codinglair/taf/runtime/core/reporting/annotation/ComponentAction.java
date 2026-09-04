package com.codinglair.taf.runtime.core.reporting.annotation;

import java.lang.annotation.*;

/** Marks a page-component action. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ComponentAction {
  String value();
}
