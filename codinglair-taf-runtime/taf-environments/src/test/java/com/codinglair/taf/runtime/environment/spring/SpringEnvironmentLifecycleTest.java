package com.codinglair.taf.runtime.environment.spring;

import static org.assertj.core.api.Assertions.*;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.environment.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SpringEnvironmentLifecycleTest {
  private static final EnvironmentType TYPE = new EnvironmentType("database");

  @Test
  void parallelSessionsOwnDistinctResourcesAndStopExactlyOnce() throws Exception {
    TrackingProvider provider = new TrackingProvider(EnvironmentMode.CONTAINER);
    var registry = registry(provider, EnvironmentMode.CONTAINER);
    var manager = new SessionEnvironmentManager(registry);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Owned> first = executor.submit(() -> provision(manager));
      Future<Owned> second = executor.submit(() -> provision(manager));
      Owned a = first.get();
      Owned b = second.get();
      assertThat(a.resource.id()).isNotEqualTo(b.resource.id());
      a.session.complete();
      a.session.cleanup();
      b.session.complete();
      assertThat(provider.stops.get()).isEqualTo(2);
    }
  }

  @Test
  void operationFailureAndCancellationStillCleanUp() {
    TrackingProvider provider = new TrackingProvider(EnvironmentMode.CONTAINER);
    var manager = new EnvironmentOperationManager(registry(provider, EnvironmentMode.CONTAINER));
    assertThatThrownBy(
            () ->
                manager.withResource(
                    request(EnvironmentMode.CONTAINER),
                    resource -> {
                      throw new CancellationException("cancelled");
                    }))
        .isInstanceOf(CancellationException.class);
    assertThat(provider.stops.get()).isEqualTo(1);
  }

  @Test
  void externalModeDoesNotCreateOrStopContainers() {
    TrackingProvider external = new TrackingProvider(EnvironmentMode.EXTERNAL);
    var manager = new SessionEnvironmentManager(registry(external, EnvironmentMode.EXTERNAL));
    TestSession session = TestSession.create();
    manager.provision(session, request(EnvironmentMode.EXTERNAL));
    session.complete();
    assertThat(external.starts.get()).isEqualTo(1);
    assertThat(external.stops.get()).isEqualTo(1);
  }

  @Test
  void propertiesPublishOnlyWhenReady() {
    var environment = new MockEnvironment();
    var publisher = new EnvironmentPropertyPublisher(environment);
    TestResource unavailable = new TestResource("unavailable", EnvironmentStatus.UNAVAILABLE);
    assertThatThrownBy(() -> publisher.publish(unavailable))
        .hasMessageContaining("before the resource is ready");
    TestResource ready = new TestResource("ready", EnvironmentStatus.READY);
    publisher.publish(ready);
    assertThat(environment.getProperty("service.port")).isEqualTo("1234");
  }

  private static Owned provision(SessionEnvironmentManager manager) {
    TestSession session = TestSession.create();
    return new Owned(session, manager.provision(session, request(EnvironmentMode.CONTAINER)));
  }

  private static EnvironmentRequest request(EnvironmentMode mode) {
    return new EnvironmentRequest("db", TYPE, mode, Set.of(), Map.of(), Duration.ofSeconds(2));
  }

  private static EnvironmentRegistry registry(EnvironmentProvider provider, EnvironmentMode mode) {
    var registry = new DefaultEnvironmentRegistry();
    registry.register(TYPE, mode, provider);
    return registry;
  }

  private record Owned(TestSession session, EnvironmentResource resource) {}

  private static final class TrackingProvider extends AbstractEnvironmentProvider {
    final AtomicInteger starts = new AtomicInteger();
    final AtomicInteger stops = new AtomicInteger();
    private final EnvironmentMode mode;

    TrackingProvider(EnvironmentMode mode) {
      this.mode = mode;
    }

    public String id() {
      return "tracking";
    }

    public Set<EnvironmentMode> supportedModes() {
      return Set.of(mode);
    }

    public PreflightResult preflight(EnvironmentRequest request) {
      return PreflightResult.from(List.of());
    }

    protected EnvironmentResource create(EnvironmentRequest request) {
      starts.incrementAndGet();
      return new TestResource(UUID.randomUUID().toString(), EnvironmentStatus.READY, stops);
    }
  }

  private static final class TestResource implements EnvironmentResource {
    private final String id;
    private final EnvironmentStatus status;
    private final AtomicInteger stops;
    private final AtomicBoolean closed = new AtomicBoolean();

    TestResource(String id, EnvironmentStatus status) {
      this(id, status, new AtomicInteger());
    }

    TestResource(String id, EnvironmentStatus status, AtomicInteger stops) {
      this.id = id;
      this.status = status;
      this.stops = stops;
    }

    public String id() {
      return id;
    }

    public EnvironmentType type() {
      return TYPE;
    }

    public EnvironmentMode mode() {
      return EnvironmentMode.CONTAINER;
    }

    public Map<String, String> properties() {
      return Map.of("service.port", "1234");
    }

    public EnvironmentDiagnostic diagnose() {
      return new EnvironmentDiagnostic(status, "state", "", Map.of(), Instant.now());
    }

    public void cleanup() {
      if (closed.compareAndSet(false, true)) stops.incrementAndGet();
    }
  }
}
