package com.codinglair.taf.migration;

/** Provisioning-boundary adapter for real database-scoped MongoDB users. */
public interface MongoCredentialStore {
  void createDatabaseUser(String database, String username, String password);

  void dropDatabaseUser(String database, String username);
}
