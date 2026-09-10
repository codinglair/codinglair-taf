package com.codinglair.taf.messaging.aws.environment;

import java.util.Objects;

/** Immutable ownership and dependency record used to authorize cleanup. */
public record AwsOwnershipManifestEntry(
    AwsResourceDescriptor resource,
    String owner,
    AwsResourceCreationSource creationSource,
    AwsResourceCleanupPolicy cleanupPolicy,
    int dependencyOrder) {
  public AwsOwnershipManifestEntry {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(owner, "owner");
    if (owner.isBlank()) throw new IllegalArgumentException("owner must not be blank");
    Objects.requireNonNull(creationSource, "creationSource");
    Objects.requireNonNull(cleanupPolicy, "cleanupPolicy");
    if (dependencyOrder < 0)
      throw new IllegalArgumentException("dependencyOrder must not be negative");
  }

  public boolean testOwned() {
    return creationSource == AwsResourceCreationSource.TAF
        && cleanupPolicy == AwsResourceCleanupPolicy.DELETE;
  }
}
