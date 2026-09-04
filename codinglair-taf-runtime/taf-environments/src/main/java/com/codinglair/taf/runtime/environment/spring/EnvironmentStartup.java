package com.codinglair.taf.runtime.environment.spring;

/** Controls whether a configured shared resource is created at context startup or first use. */
public enum EnvironmentStartup {
  EAGER,
  LAZY
}
