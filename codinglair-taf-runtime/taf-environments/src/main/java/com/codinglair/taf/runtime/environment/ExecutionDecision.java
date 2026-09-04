package com.codinglair.taf.runtime.environment;

/** Explicit preflight policy outcome. */
public record ExecutionDecision(boolean permitted, boolean warning, String reason) {
  public static ExecutionDecision allowed() {
    return new ExecutionDecision(true, false, "Environment is ready");
  }

  public static ExecutionDecision allowedWithWarnings(String reason) {
    return new ExecutionDecision(true, true, reason);
  }

  public static ExecutionDecision blocked(String reason) {
    return new ExecutionDecision(false, false, reason);
  }
}
