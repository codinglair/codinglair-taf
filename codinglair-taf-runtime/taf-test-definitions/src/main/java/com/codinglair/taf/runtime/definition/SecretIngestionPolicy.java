package com.codinglair.taf.runtime.definition;

/** Policy selected for a distinct test-data ingestion operation. */
public enum SecretIngestionPolicy {
  ENFORCE,
  NORMALIZE_AND_PERSIST
}
