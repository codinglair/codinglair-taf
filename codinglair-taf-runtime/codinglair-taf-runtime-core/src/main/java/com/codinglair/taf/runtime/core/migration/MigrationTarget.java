package com.codinglair.taf.runtime.core.migration;

/** Ownership boundary used when authorizing a migration operation. */
public enum MigrationTarget {
  FRAMEWORK_CONTEXT,
  TESTCONTAINERS_SUT,
  EXTERNAL_SHARED_SUT,
  PRODUCTION_SUT
}
