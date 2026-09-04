package com.codinglair.taf.runtime.core.reporting.annotation;

import java.lang.annotation.*;

/** Marks an API object-model action. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiAction {
  String value();
}
