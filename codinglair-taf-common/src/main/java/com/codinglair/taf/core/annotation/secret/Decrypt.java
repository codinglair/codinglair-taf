package com.codinglair.taf.core.annotation.secret;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD) // Targeting the getter since the fields are going to be private
public @interface Decrypt {
}
