package com.codinglair.taf.mcp.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure state-transition rules for durable jobs. */
public final class JobStateMachine {
  private static final Map<JobState, EnumSet<JobState>> TRANSITIONS = transitions();

  private JobStateMachine() {}

  public static Job transition(Job job, JobState target, Instant now) {
    if (job.state() == target && target == JobState.CANCEL_REQUESTED) {
      return job;
    }
    if (job.state().terminal() || !TRANSITIONS.get(job.state()).contains(target)) {
      throw new InvalidJobTransitionException(job.state(), target);
    }
    return copy(
        job,
        target,
        job.progressPercent(),
        job.safeCheckpoint(),
        job.resultReferences(),
        job.events(),
        now);
  }

  public static Job progress(Job job, int percent, String kind, String message, Instant now) {
    if (job.state() != JobState.RUNNING || percent < job.progressPercent() || percent > 100) {
      throw new IllegalStateException(
          "Progress requires RUNNING state and cannot decrease or exceed 100");
    }
    var events = new ArrayList<>(job.events());
    events.add(new JobEvent(events.size() + 1L, now, kind, message));
    return copy(
        job, job.state(), percent, job.safeCheckpoint(), job.resultReferences(), events, now);
  }

  public static Job checkpoint(Job job, String safeBoundary, Instant now) {
    if (job.state() != JobState.RUNNING) {
      throw new IllegalStateException("A safe checkpoint can only be recorded while RUNNING");
    }
    return copy(
        job,
        job.state(),
        job.progressPercent(),
        Optional.of(safeBoundary),
        job.resultReferences(),
        job.events(),
        now);
  }

  public static Job succeed(Job job, List<JobReference> references, Instant now) {
    if (!TRANSITIONS.get(job.state()).contains(JobState.SUCCEEDED)) {
      throw new InvalidJobTransitionException(job.state(), JobState.SUCCEEDED);
    }
    return copy(job, JobState.SUCCEEDED, 100, job.safeCheckpoint(), references, job.events(), now);
  }

  public static Job recoverAfterRestart(Job job, Instant now) {
    if (job.state() != JobState.RUNNING) {
      return job;
    }
    return copy(
        job,
        JobState.RECOVERY_PENDING,
        job.progressPercent(),
        job.safeCheckpoint(),
        job.resultReferences(),
        job.events(),
        now);
  }

  static void validateReplacement(Job current, Job replacement) {
    if (!current.id().equals(replacement.id())
        || !current.operation().equals(replacement.operation())
        || !current.payload().equals(replacement.payload())
        || !current.createdAt().equals(replacement.createdAt())) {
      throw new IllegalArgumentException("Immutable job identity and payload fields cannot change");
    }
    if (current.state().terminal()) {
      throw new InvalidJobTransitionException(current.state(), replacement.state());
    }
    if (replacement.events().size() < current.events().size()
        || !replacement.events().subList(0, current.events().size()).equals(current.events())) {
      throw new IllegalArgumentException("Persisted job events are append-only");
    }
    if (replacement.state() != JobState.SUCCEEDED
        && !replacement.resultReferences().equals(current.resultReferences())) {
      throw new IllegalArgumentException("Result references can only be attached on success");
    }
    boolean sameStateUpdate =
        current.state() == replacement.state()
            && current.state() == JobState.RUNNING
            && replacement.progressPercent() >= current.progressPercent();
    if (!sameStateUpdate && !TRANSITIONS.get(current.state()).contains(replacement.state())) {
      throw new InvalidJobTransitionException(current.state(), replacement.state());
    }
  }

  private static Job copy(
      Job job,
      JobState state,
      int progress,
      Optional<String> checkpoint,
      List<JobReference> references,
      List<JobEvent> events,
      Instant now) {
    return new Job(
        job.id(),
        job.operation(),
        job.payload(),
        state,
        progress,
        checkpoint,
        references,
        events,
        job.version() + 1,
        job.createdAt(),
        now);
  }

  private static Map<JobState, EnumSet<JobState>> transitions() {
    var transitions = new EnumMap<JobState, EnumSet<JobState>>(JobState.class);
    transitions.put(
        JobState.QUEUED,
        EnumSet.of(JobState.RUNNING, JobState.CANCEL_REQUESTED, JobState.CANCELLED));
    transitions.put(
        JobState.RUNNING,
        EnumSet.of(
            JobState.CANCEL_REQUESTED,
            JobState.RECOVERY_PENDING,
            JobState.SUCCEEDED,
            JobState.FAILED));
    transitions.put(JobState.CANCEL_REQUESTED, EnumSet.of(JobState.CANCELLED, JobState.FAILED));
    transitions.put(
        JobState.RECOVERY_PENDING,
        EnumSet.of(JobState.QUEUED, JobState.CANCEL_REQUESTED, JobState.CANCELLED));
    transitions.put(JobState.SUCCEEDED, EnumSet.noneOf(JobState.class));
    transitions.put(JobState.FAILED, EnumSet.noneOf(JobState.class));
    transitions.put(JobState.CANCELLED, EnumSet.noneOf(JobState.class));
    return Map.copyOf(transitions);
  }
}
