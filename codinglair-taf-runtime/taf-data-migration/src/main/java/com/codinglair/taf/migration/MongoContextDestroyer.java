package com.codinglair.taf.migration;

/** Destroys a framework-owned Mongo context after an unusable partial migration. */
@FunctionalInterface
public interface MongoContextDestroyer {
  void destroy(String targetIdentity);
}
