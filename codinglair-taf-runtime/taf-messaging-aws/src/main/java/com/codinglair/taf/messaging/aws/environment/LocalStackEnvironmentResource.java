package com.codinglair.taf.messaging.aws.environment;

import com.codinglair.taf.runtime.environment.EnvironmentResource;

/** Typed LocalStack environment result with its immutable ownership snapshot. */
public interface LocalStackEnvironmentResource extends EnvironmentResource {
  AwsOwnershipManifest ownershipManifest();

  /** Effective connection configuration containing the dynamically allocated endpoint. */
  AwsEnvironmentConfiguration effectiveConfiguration();
}
