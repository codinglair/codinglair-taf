package com.codinglair.taf.runtime.core;

import com.codinglair.taf.runtime.core.context.CorrelationContext;
import com.codinglair.taf.runtime.core.context.TestContext;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerRegistry;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolated mutable execution scope owning controllers, artifacts, and cleanup. */
public final class TestSession implements AutoCloseable {
  private final String sessionId;
  private final Instant startTime = Instant.now();
  private final ControllerRegistry controllerRegistry;
  private final CopyOnWriteArrayList<CleanupListener> cleanupListeners =
      new CopyOnWriteArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final CorrelationContext correlationContext = CorrelationContext.create();
  private final TestContext testContext = TestContext.create(correlationContext);
  private final ArtifactCollector artifactCollector;

  private TestSession(
      String sessionId, ControllerRegistry registry, EnvironmentAccess environments) {
    this.sessionId = Objects.requireNonNull(sessionId);
    this.controllerRegistry = Objects.requireNonNull(registry);
    this.artifactCollector =
        new ArtifactCollector(
            TafTest.of("session", TestSession.class.getName()), sessionId, sessionId);
    registry.attach(new ControllerContext(sessionId, environments, artifactCollector));
  }

  public static TestSession create() {
    return create(EnvironmentAccess.unavailable());
  }

  public static TestSession create(EnvironmentAccess environments) {
    return new TestSession(UUID.randomUUID().toString(), new ControllerRegistry(), environments);
  }

  public static TestSession create(
      String sessionId, ControllerRegistry registry, List<CleanupListener> listeners) {
    TestSession session = new TestSession(sessionId, registry, EnvironmentAccess.unavailable());
    session.cleanupListeners.addAll(listeners);
    return session;
  }

  public String getSessionId() {
    return sessionId;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public ControllerRegistry getControllerRegistry() {
    return controllerRegistry;
  }

  public ArtifactCollector getArtifactCollector() {
    return artifactCollector;
  }

  public TestContext getTestContext() {
    return testContext;
  }

  public CorrelationContext getCorrelationContext() {
    return correlationContext;
  }

  public Map<String, Object> getAttributes() {
    return Map.of("sessionId", sessionId, "startTime", startTime);
  }

  public <T extends TestController> T getController(Class<T> type, String name) {
    return controllerRegistry.get(type, name);
  }

  public void addCleanupListener(CleanupListener listener) {
    if (closed.get())
      throw new IllegalStateException("TestSession is already closed: " + sessionId);
    cleanupListeners.add(Objects.requireNonNull(listener));
  }

  public void complete() {
    testContext.complete(Instant.now());
    close();
  }

  public void cleanup() {
    close();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    Throwable failure = null;
    try {
      controllerRegistry.close();
    } catch (Throwable current) {
      failure = current;
    }
    List<CleanupListener> listeners = new ArrayList<>(cleanupListeners);
    Collections.reverse(listeners);
    for (CleanupListener listener : listeners) {
      try {
        listener.onCleanup(sessionId);
      } catch (Throwable current) {
        if (failure == null) failure = current;
        else failure.addSuppressed(current);
      }
    }
    if (failure != null) throwUnchecked(failure);
  }

  private static void throwUnchecked(Throwable failure) {
    TestSession.<RuntimeException>throwAny(failure);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }

  @FunctionalInterface
  public interface CleanupListener {
    void onCleanup(String sessionId);
  }
}
