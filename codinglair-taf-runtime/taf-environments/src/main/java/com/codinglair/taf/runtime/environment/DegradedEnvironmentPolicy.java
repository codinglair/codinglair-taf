package com.codinglair.taf.runtime.environment;

import com.codinglair.taf.core.Capability;
import java.util.Set;

/** Policy for deciding whether preflight permits execution. */
@FunctionalInterface
public interface DegradedEnvironmentPolicy {
  ExecutionDecision evaluate(PreflightResult result);

  static DegradedEnvironmentPolicy allowWithWarningsUnlessBlocked(
      Set<Capability> blockedCapabilities) {
    Set<Capability> blocked = Set.copyOf(blockedCapabilities);
    return result -> {
      if (result.status() == EnvironmentStatus.READY) {
        return ExecutionDecision.allowed();
      }
      if (result.status() == EnvironmentStatus.DEGRADED) {
        boolean affectedBlocked =
            result.checks().stream()
                .filter(check -> check.status() == EnvironmentStatus.DEGRADED)
                .flatMap(check -> check.capability().stream())
                .anyMatch(blocked::contains);
        return affectedBlocked
            ? ExecutionDecision.blocked("A degraded required capability is blocked by policy")
            : ExecutionDecision.allowedWithWarnings("Environment is degraded");
      }
      return ExecutionDecision.blocked("Environment is " + result.status().name().toLowerCase());
    };
  }
}
