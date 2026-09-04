package com.codinglair.taf.runtime.core.reporting.annotation;

import java.lang.annotation.*;

/** Marks a consumer business workflow. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Workflow {
  String value();
}
