package com.codinglair.taf.runtime.environment;

/** Stable readiness classification required by FR-ENV-004. */
public enum EnvironmentStatus {
  READY,
  DEGRADED,
  UNAVAILABLE,
  MISCONFIGURED,
  UNKNOWN
}
