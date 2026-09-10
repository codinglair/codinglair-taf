package com.codinglair.taf.messaging.aws.environment;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable snapshot of resources owned or referenced by one environment run. */
public record AwsOwnershipManifest(String owner, List<AwsOwnershipManifestEntry> entries) {
  public AwsOwnershipManifest {
    Objects.requireNonNull(owner, "owner");
    if (owner.isBlank()) throw new IllegalArgumentException("owner must not be blank");
    entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    if (entries.stream().anyMatch(entry -> !owner.equals(entry.owner())))
      throw new IllegalArgumentException("Every manifest entry must have the manifest owner");
  }

  public List<AwsOwnershipManifestEntry> testOwnedInCleanupOrder() {
    return entries.stream()
        .filter(AwsOwnershipManifestEntry::testOwned)
        .sorted(Comparator.comparingInt(AwsOwnershipManifestEntry::dependencyOrder).reversed())
        .toList();
  }
}
