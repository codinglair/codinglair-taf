package com.codinglair.taf.migration;

import com.codinglair.taf.runtime.core.migration.DataMigrationManager;
import com.codinglair.taf.runtime.core.migration.MigrationRequest;
import com.codinglair.taf.runtime.core.migration.MigrationResult;

final class RoutingDataMigrationManager implements DataMigrationManager {
  private final DataMigrationManager relational;
  private final DataMigrationManager mongo;

  RoutingDataMigrationManager(DataMigrationManager relational, DataMigrationManager mongo) {
    this.relational = relational;
    this.mongo = mongo;
  }

  @Override
  public MigrationResult execute(MigrationRequest request) {
    return request.technology().equalsIgnoreCase("mongodb")
        ? mongo.execute(request)
        : relational.execute(request);
  }
}
