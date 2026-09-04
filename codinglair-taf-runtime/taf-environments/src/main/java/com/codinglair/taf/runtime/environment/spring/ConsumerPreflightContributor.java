package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.PreflightCheckResult;
import java.util.List;

/**
 * Adds dependency-specific, sanitized checks to consumer preflight.
 *
 * @deprecated framework modules should implement the Runtime Core neutral contributor contract.
 */
@Deprecated(forRemoval = false, since = "1.0")
public interface ConsumerPreflightContributor {
  /** Capability type owned by the contributing starter, for example {@code web-playwright}. */
  String capabilityType();

  List<PreflightCheckResult> check(
      String instanceName,
      ConsumerConfigurationProperties.CapabilityInstance instance,
      ConsumerConfigurationProperties configuration);
}
