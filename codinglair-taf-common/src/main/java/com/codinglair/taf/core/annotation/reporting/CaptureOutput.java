package com.codinglair.taf.core.annotation.reporting;

import java.lang.annotation.*;

/**
 * Annotation to capture output from methods for reporting. Used to capture console output, logs, or
 * other side effects.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CaptureOutput {

  /** Name for the captured output. */
  String value() default "";

  /** Whether to capture stdout. */
  boolean captureStdout() default true;

  /** Whether to capture stderr. */
  boolean captureStderr() default true;
}
