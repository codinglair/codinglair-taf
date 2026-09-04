package com.codinglair.taf.core.annotation.secret;

import java.lang.annotation.*;

/**
 * Annotation to mark fields or methods that contain secret values. Used by SecretAspect to handle
 * secrets appropriately.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Decrypt {

  /** The decryption key alias or reference. */
  String value();
}
