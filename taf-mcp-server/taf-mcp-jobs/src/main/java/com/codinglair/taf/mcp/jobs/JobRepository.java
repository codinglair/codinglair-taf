package com.codinglair.taf.mcp.jobs;

import java.util.List;
import java.util.Optional;

/** Location-independent durable job repository SPI. */
public interface JobRepository {
  Job create(Job job);

  Optional<Job> find(JobId id);

  Job save(Job job, long expectedVersion);

  List<Job> findRecoverable();
}
