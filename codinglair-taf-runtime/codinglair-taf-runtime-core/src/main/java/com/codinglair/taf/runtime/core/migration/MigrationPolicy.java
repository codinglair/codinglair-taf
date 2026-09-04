package com.codinglair.taf.runtime.core.migration;

/** Operation permitted for one logical migration target. */
public enum MigrationPolicy {
  DISABLED,
  VALIDATE_ONLY,
  VALIDATE_AND_MIGRATE
}
