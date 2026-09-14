package com.codinglair.taf.messaging.aws.environment;

import com.codinglair.taf.runtime.environment.EnvironmentDiagnostic;
import com.codinglair.taf.runtime.environment.EnvironmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Extension boundary for service-specific provisioning without coupling controllers to it. */
public interface AwsServiceEnvironmentContributor {
  String service();

  List<AwsResourceDescriptor> describeResources(String profileName);

  /** Provisions only resources owned by the requesting environment. */
  default List<AwsResourceDescriptor> provision(String profileName) {
    return List.of();
  }

  /** Reports service-specific readiness without exposing credentials. */
  default EnvironmentDiagnostic readiness(String profileName) {
    return new EnvironmentDiagnostic(
        EnvironmentStatus.UNKNOWN,
        "Readiness is not implemented for this AWS service contributor",
        "Provide a service-specific readiness implementation",
        Map.of("service", service()),
        Instant.EPOCH);
  }

  /** Supplies bounded, sanitized failure-time diagnostics. */
  default EnvironmentDiagnostic diagnostics(String profileName) {
    return readiness(profileName);
  }

  /** Idempotently cleans the supplied test-owned resources. */
  default void cleanup(String profileName, List<AwsResourceDescriptor> resources) {}
}
