package com.codinglair.taf.database.lifecycle;

/** Governed lifecycle applied independently to each logical SUT database. */
public enum DatabaseLifecyclePolicy {
  EPHEMERAL,
  RESET,
  SNAPSHOT_ON_FAILURE,
  RETAIN_WITH_TTL,
  EXTERNAL
}
