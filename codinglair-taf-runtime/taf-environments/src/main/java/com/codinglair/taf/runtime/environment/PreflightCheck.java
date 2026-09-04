package com.codinglair.taf.runtime.environment;

import com.codinglair.taf.core.Capability;
import java.util.Optional;

/** A suite-wide or capability-specific readiness check. */
public interface PreflightCheck {
  String id();

  PreflightCheckType type();

  Optional<Capability> capability();

  PreflightCheckResult execute(EnvironmentRequest request);
}
