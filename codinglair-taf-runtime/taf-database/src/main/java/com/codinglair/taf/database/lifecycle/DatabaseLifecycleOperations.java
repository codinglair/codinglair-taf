package com.codinglair.taf.database.lifecycle;

/**
 * Technology-specific operations. Implementations must scope every action to the supplied
 * namespace.
 */
public interface DatabaseLifecycleOperations {
  void provision(DatabaseLifecycleRequest request);

  void verifyReady(DatabaseLifecycleRequest request);

  void migrate(DatabaseLifecycleRequest request);

  void reset(DatabaseLifecycleRequest request);

  void seed(DatabaseLifecycleRequest request);

  String exportSnapshot(DatabaseLifecycleRequest request);

  void cleanup(DatabaseLifecycleRequest request);
}
