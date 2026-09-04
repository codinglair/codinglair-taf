package com.codinglair.taf.mcp.stdio;

import com.codinglair.taf.mcp.jobs.JobConflictException;
import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobService;
import com.codinglair.taf.mcp.jobs.JobState;
import com.codinglair.taf.mcp.jobs.JobStateMachine;
import com.codinglair.taf.mcp.tools.McpWorkflowTools;
import java.time.Clock;
import java.util.Objects;

/** Coordinates process shutdown with durable job cancellation and worker interruption. */
public final class StdioShutdownCoordinator implements AutoCloseable {
  private final McpWorkflowTools workflows;
  private final JobService jobs;
  private final JobRepository repository;
  private final Clock clock;

  public StdioShutdownCoordinator(
      McpWorkflowTools workflows, JobService jobs, JobRepository repository, Clock clock) {
    this.workflows = Objects.requireNonNull(workflows, "workflows");
    this.jobs = Objects.requireNonNull(jobs, "jobs");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public void close() {
    repository.findRecoverable().forEach(job -> jobs.cancel(job.id()));
    workflows.close();
    repository.findRecoverable().stream()
        .filter(job -> job.state() == JobState.CANCEL_REQUESTED)
        .forEach(
            job -> {
              try {
                repository.save(
                    JobStateMachine.transition(job, JobState.CANCELLED, clock.instant()),
                    job.version());
              } catch (JobConflictException ignored) {
                // The worker completed the same cancellation concurrently.
              }
            });
  }
}
