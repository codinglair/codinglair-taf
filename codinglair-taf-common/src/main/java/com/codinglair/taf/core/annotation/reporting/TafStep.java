package com.codinglair.taf.core.annotation.reporting;

import java.lang.annotation.*;

/**
 * Annotation to mark a method as a test step. Used by the ReportingAspect to track and report
 * individual steps.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TafStep {

  /** Name/description for this test step. */
  String value();
}
