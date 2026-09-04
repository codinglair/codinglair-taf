package com.codinglair.taf.core.annotation.data;

import java.lang.annotation.*;

/**
 * Annotation to identify a test case uniquely within the test suite. Used for correlation and
 * reporting purposes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TestCaseId {

  /** Unique identifier for this test case. */
  String value();

  /** Optional description for the test case. */
  String description() default "";
}
