package com.codinglair.taf.migration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

record MongoMigrationJob(
    String image,
    String networkId,
    String uri,
    String history,
    String operation,
    List<Path> locations,
    Duration timeout,
    int maximumOutputBytes,
    long memoryBytes,
    long cpuCount,
    long pidLimit,
    long tmpfsBytes) {
  MongoMigrationJob {
    locations = List.copyOf(locations);
  }

  @Override
  public String toString() {
    return "MongoMigrationJob[image="
        + image
        + ", operation="
        + operation
        + ", locations="
        + locations.size()
        + ", protectedUri=REDACTED]";
  }
}

record MongoMigrationJobResult(int exitCode, String output, Duration duration) {}

@FunctionalInterface
interface MongoMigrationJobExecutor {
  MongoMigrationJobResult execute(MongoMigrationJob job);
}
