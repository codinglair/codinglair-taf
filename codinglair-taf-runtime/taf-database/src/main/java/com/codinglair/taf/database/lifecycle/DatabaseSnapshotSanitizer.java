package com.codinglair.taf.database.lifecycle;

@FunctionalInterface
public interface DatabaseSnapshotSanitizer {
  String sanitize(String snapshot);
}
