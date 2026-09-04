package com.codinglair.taf.database.lifecycle;

public interface LifecycleEvidenceSink {
  void snapshot(String executionId, String database, String sanitizedContent);

  void audit(RetentionMetadata metadata);
}
