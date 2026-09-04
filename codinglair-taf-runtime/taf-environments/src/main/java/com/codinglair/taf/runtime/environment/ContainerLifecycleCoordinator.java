package com.codinglair.taf.runtime.environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Coordinates isolated ownership and reference-counted shared container leases. */
public final class ContainerLifecycleCoordinator implements AutoCloseable {
  private final Object monitor = new Object();
  private final Map<String, SharedContainer> shared = new HashMap<>();

  ContainerLease acquire(
      EnvironmentRequest request,
      EnvironmentType type,
      Supplier<? extends ManagedContainer> factory) {
    Objects.requireNonNull(request, "request");
    ContainerLifecycle lifecycle = ContainerLifecycle.from(request);
    if (lifecycle == ContainerLifecycle.ISOLATED) {
      ManagedContainer container = start(factory);
      try {
        return lease(
            request.resourceName() + "-" + UUID.randomUUID(),
            type,
            lifecycle,
            container,
            container::stop);
      } catch (RuntimeException failure) {
        stopAfterFailure(container, failure);
        throw failure;
      }
    }

    String key =
        request
            .properties()
            .getOrDefault(
                ContainerLifecycle.SHARED_KEY_PROPERTY,
                request.type().name() + ":" + request.resourceName());
    if (key.isBlank()) {
      throw new IllegalArgumentException("Shared container key must not be blank");
    }
    synchronized (monitor) {
      SharedContainer state = shared.get(key);
      if (state == null) {
        state = new SharedContainer(start(factory));
        shared.put(key, state);
      }
      state.references++;
      SharedContainer acquired = state;
      try {
        return lease(
            request.resourceName() + "-" + UUID.randomUUID(),
            type,
            lifecycle,
            state.container,
            () -> releaseShared(key, acquired));
      } catch (RuntimeException failure) {
        try {
          releaseShared(key, acquired);
        } catch (RuntimeException cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
        throw failure;
      }
    }
  }

  private static ManagedContainer start(Supplier<? extends ManagedContainer> factory) {
    if (Thread.currentThread().isInterrupted()) {
      throw new ContainerCancellationException(
          "Container provisioning was cancelled before startup");
    }
    ManagedContainer container =
        Objects.requireNonNull(factory.get(), "Container factory returned null");
    try {
      container.start();
      if (Thread.currentThread().isInterrupted()) {
        throw new ContainerCancellationException(
            "Container provisioning was cancelled during startup");
      }
      return container;
    } catch (RuntimeException failure) {
      stopAfterFailure(container, failure);
      throw failure;
    }
  }

  private static void stopAfterFailure(ManagedContainer container, RuntimeException original) {
    try {
      container.stop();
    } catch (RuntimeException cleanupFailure) {
      original.addSuppressed(cleanupFailure);
    }
  }

  private ContainerLease lease(
      String id,
      EnvironmentType type,
      ContainerLifecycle lifecycle,
      ManagedContainer container,
      Runnable cleanup) {
    return new ContainerLease(id, type, lifecycle, container, cleanup);
  }

  private void releaseShared(String key, SharedContainer expected) {
    synchronized (monitor) {
      SharedContainer current = shared.get(key);
      if (current != expected) {
        return;
      }
      current.references--;
      if (current.references == 0) {
        shared.remove(key);
        current.container.stop();
      }
    }
  }

  int sharedContainerCount() {
    synchronized (monitor) {
      return shared.size();
    }
  }

  @Override
  public void close() {
    RuntimeException aggregate = null;
    synchronized (monitor) {
      for (SharedContainer state : shared.values()) {
        try {
          state.container.stop();
        } catch (RuntimeException failure) {
          if (aggregate == null) {
            aggregate = new IllegalStateException("One or more shared containers failed cleanup");
          }
          aggregate.addSuppressed(failure);
        }
      }
      shared.clear();
    }
    if (aggregate != null) {
      throw aggregate;
    }
  }

  private static final class SharedContainer {
    private final ManagedContainer container;
    private int references;

    private SharedContainer(ManagedContainer container) {
      this.container = container;
    }
  }

  static final class ContainerLease implements EnvironmentResource {
    private final String id;
    private final EnvironmentType type;
    private final ContainerLifecycle lifecycle;
    private final ManagedContainer container;
    private final Runnable cleanup;
    private final AtomicBoolean cleaned = new AtomicBoolean();
    private final Map<String, String> properties;

    private ContainerLease(
        String id,
        EnvironmentType type,
        ContainerLifecycle lifecycle,
        ManagedContainer container,
        Runnable cleanup) {
      this.id = id;
      this.type = type;
      this.lifecycle = lifecycle;
      this.container = container;
      this.cleanup = cleanup;
      this.properties = container.properties();
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public EnvironmentType type() {
      return type;
    }

    @Override
    public EnvironmentMode mode() {
      return EnvironmentMode.CONTAINER;
    }

    @Override
    public Map<String, String> properties() {
      return properties;
    }

    public ContainerLifecycle lifecycle() {
      return lifecycle;
    }

    @Override
    public EnvironmentDiagnostic diagnose() {
      EnvironmentStatus status =
          container.isRunning() ? EnvironmentStatus.READY : EnvironmentStatus.UNAVAILABLE;
      return new EnvironmentDiagnostic(
          status,
          status == EnvironmentStatus.READY ? "Container is running" : "Container is not running",
          status == EnvironmentStatus.READY
              ? ""
              : "Inspect Docker availability and bounded container logs",
          Map.of("logs", container.logs()),
          java.time.Instant.now());
    }

    @Override
    public void cleanup() {
      if (cleaned.compareAndSet(false, true)) {
        cleanup.run();
      }
    }
  }
}
