package com.codinglair.taf.core.annotation.reporting;

import java.lang.annotation.*;

/**
 * Annotation to add a description to a test step or phase. Used by the ReportingAspect to enhance
 * test reports.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TafDescription {

  /** Description text for the test step. */
  String value();
}
