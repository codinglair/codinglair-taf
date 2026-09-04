package com.codinglair.taf.mcp.jobs;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Application service for creation, cancellation, and restart recovery. */
public final class JobService {
  private final JobRepository repository;
  private final Clock clock;

  public JobService(JobRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public Job create(String operation, Map<String, String> sanitizedPayload) {
    return repository.create(
        Job.queued(JobId.create(), operation, sanitizedPayload, clock.instant()));
  }

  public Job cancel(JobId id) {
    while (true) {
      Job current =
          repository
              .find(id)
              .orElseThrow(() -> new NoSuchElementException("Job not found: " + id.value()));
      if (current.state() == JobState.CANCEL_REQUESTED || current.state() == JobState.CANCELLED)
        return current;
      if (current.state().terminal()) return current;
      Job requested =
          JobStateMachine.transition(current, JobState.CANCEL_REQUESTED, clock.instant());
      try {
        return repository.save(requested, current.version());
      } catch (JobConflictException ignored) {
        // Re-read and converge on the idempotent target state.
      }
    }
  }

  public List<Job> recoverAfterRestart() {
    var recovered = new ArrayList<Job>();
    for (Job current : repository.findRecoverable()) {
      Job replacement = JobStateMachine.recoverAfterRestart(current, clock.instant());
      if (replacement == current) {
        recovered.add(current);
      } else {
        try {
          recovered.add(repository.save(replacement, current.version()));
        } catch (JobConflictException ignored) {
          repository.find(current.id()).ifPresent(recovered::add);
        }
      }
    }
    return List.copyOf(recovered);
  }
}
