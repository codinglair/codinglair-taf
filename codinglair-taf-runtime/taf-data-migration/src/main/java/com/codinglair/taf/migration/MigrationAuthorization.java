package com.codinglair.taf.migration;

import com.codinglair.taf.runtime.core.migration.MigrationRequest;

/** Environment-owned authorization boundary; configuration properties cannot replace it. */
@FunctionalInterface
public interface MigrationAuthorization {
  boolean permits(MigrationRequest request);
}
