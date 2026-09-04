package com.codinglair.taf.mcp.worker;

import com.codinglair.taf.mcp.jobs.Job;
import com.codinglair.taf.mcp.jobs.JobConflictException;
import com.codinglair.taf.mcp.jobs.JobRepository;
import com.codinglair.taf.mcp.jobs.JobState;
import com.codinglair.taf.mcp.jobs.JobStateMachine;
import java.time.Clock;
import java.util.Objects;

/** Bridges durable job cancellation/state with one isolated worker execution. */
public final class JobExecutionCoordinator {
  private static final int MAXIMUM_CONFLICT_RETRIES = 16;
  private final JobRepository repository;
  private final LocalExecutionWorker worker;
  private final Clock clock;

  public JobExecutionCoordinator(
      JobRepository repository, LocalExecutionWorker worker, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Job execute(WorkerRequest request) {
    var running = update(request, JobState.RUNNING);
    if (running.state() == JobState.CANCELLED) {
      return running;
    }
    var result = worker.execute(request, () -> cancellationRequested(request));
    return complete(request, result);
  }

  private Job complete(WorkerRequest request, WorkerResult result) {
    for (var attempt = 0; attempt < MAXIMUM_CONFLICT_RETRIES; attempt++) {
      var current = repository.find(request.jobId()).orElseThrow(() -> missing(request));
      try {
        if (current.state() == JobState.CANCEL_REQUESTED
            || result.status() == WorkerStatus.CANCELLED) {
          return repository.save(
              JobStateMachine.transition(current, JobState.CANCELLED, clock.instant()),
              current.version());
        }
        if (result.status() == WorkerStatus.SUCCEEDED) {
          var references =
              result.artifacts().stream().map(ArtifactManifestEntry::reference).toList();
          return repository.save(
              JobStateMachine.succeed(current, references, clock.instant()), current.version());
        }
        return repository.save(
            JobStateMachine.transition(current, JobState.FAILED, clock.instant()),
            current.version());
      } catch (JobConflictException _) {
        // A concurrent cancellation is expected; reload and converge on its terminal
        // acknowledgement.
      }
    }
    throw new WorkerExecutionException(
        "JOB_CONFLICT", "Worker could not persist terminal job state");
  }

  private Job update(WorkerRequest request, JobState target) {
    var current = repository.find(request.jobId()).orElseThrow(() -> missing(request));
    if (current.state() == JobState.CANCEL_REQUESTED) {
      return repository.save(
          JobStateMachine.transition(current, JobState.CANCELLED, clock.instant()),
          current.version());
    }
    return repository.save(
        JobStateMachine.transition(current, target, clock.instant()), current.version());
  }

  private boolean cancellationRequested(WorkerRequest request) {
    return repository
        .find(request.jobId())
        .map(job -> job.state() == JobState.CANCEL_REQUESTED)
        .orElse(true);
  }

  private static WorkerExecutionException missing(WorkerRequest request) {
    return new WorkerExecutionException("JOB_NOT_FOUND", "Worker job does not exist");
  }
}
