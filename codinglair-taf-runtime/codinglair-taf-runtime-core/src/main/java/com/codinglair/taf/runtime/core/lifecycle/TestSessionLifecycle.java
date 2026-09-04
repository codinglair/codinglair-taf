package com.codinglair.taf.runtime.core.lifecycle;

import com.codinglair.taf.runtime.core.TestSession;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Shared mechanics used only by the framework-owned TestNG and Cucumber lifecycle owners. */
public final class TestSessionLifecycle implements CurrentTestSession {
  private final TestSessionFactory factory;
  private final ConcurrentHashMap<String, Ownership> active = new ConcurrentHashMap<>();
  private final ThreadLocal<Ownership> current = new ThreadLocal<>();

  public TestSessionLifecycle(TestSessionFactory factory) {
    this.factory = Objects.requireNonNull(factory, "factory");
  }

  public TestSession open(InvocationDescriptor descriptor) {
    Objects.requireNonNull(descriptor, "descriptor");
    if (current.get() != null) {
      throw new IllegalStateException(
          "Thread already owns invocation " + current.get().descriptor.id());
    }
    Ownership reservation = new Ownership(descriptor, null, Thread.currentThread().threadId());
    if (active.putIfAbsent(descriptor.id(), reservation) != null) {
      throw new IllegalStateException(
          "Invocation already has a lifecycle owner: " + descriptor.id());
    }
    try {
      TestSession session =
          Objects.requireNonNull(factory.create(), "TestSessionFactory returned null");
      Ownership ownership = new Ownership(descriptor, session, reservation.threadId);
      active.replace(descriptor.id(), reservation, ownership);
      current.set(ownership);
      return session;
    } catch (Throwable failure) {
      active.remove(descriptor.id(), reservation);
      throw failure;
    }
  }

  public void close(InvocationDescriptor descriptor, InvocationOutcome outcome) {
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(outcome, "outcome");
    Ownership ownership = current.get();
    if (ownership == null || !ownership.descriptor.id().equals(descriptor.id())) {
      throw new IllegalStateException(
          "Invocation is not owned by the current thread: " + descriptor.id());
    }
    Throwable primary = outcome.failure();
    try {
      ownership.session.complete();
    } catch (Throwable cleanupFailure) {
      if (primary == null) primary = cleanupFailure;
      else if (primary != cleanupFailure) primary.addSuppressed(cleanupFailure);
    } finally {
      current.remove();
      active.remove(descriptor.id(), ownership);
    }
    // The runner already owns and propagates the supplied invocation failure. Re-throw only a
    // cleanup-only failure; otherwise the cleanup failure is retained as suppressed evidence.
    if (outcome.failure() == null && primary != null) throwUnchecked(primary);
  }

  @Override
  public Optional<TestSession> find() {
    Ownership ownership = current.get();
    return ownership == null ? Optional.empty() : Optional.of(ownership.session);
  }

  /** Snapshot for leak detection and lifecycle ownership diagnostics. */
  public Map<String, OwnershipDiagnostic> activeOwnerships() {
    return active.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry ->
                    new OwnershipDiagnostic(
                        entry.getValue().descriptor.owner(), entry.getValue().threadId)));
  }

  public record OwnershipDiagnostic(String owner, long threadId) {}

  private record Ownership(InvocationDescriptor descriptor, TestSession session, long threadId) {}

  private static void throwUnchecked(Throwable failure) {
    TestSessionLifecycle.<RuntimeException>throwAny(failure);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }
}
