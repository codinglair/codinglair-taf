package com.codinglair.taf.runtime.environment.spring;

/** Cleanup policy for Spring-composed environment resources. */
public enum EnvironmentCleanupPolicy {
  ALWAYS,
  ON_SUCCESS,
  NEVER
}
