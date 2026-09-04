package com.codinglair.taf.runtime.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ContainerLifecycleCoordinatorTest {
  private static final EnvironmentType DATABASE = new EnvironmentType("database");

  @Test
  void parallelIsolatedContainersHaveIndependentDynamicEndpoints() throws Exception {
    ContainerLifecycleCoordinator coordinator = new ContainerLifecycleCoordinator();
    AtomicInteger ports = new AtomicInteger(20_000);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var tasks =
          java.util.stream.IntStream.range(0, 32)
              .<java.util.concurrent.Callable<EnvironmentResource>>mapToObj(
                  index ->
                      () ->
                          coordinator.acquire(
                              request("isolated-" + index, ContainerLifecycle.ISOLATED),
                              DATABASE,
                              () -> FakeContainer.success(ports.incrementAndGet())))
              .toList();
      List<EnvironmentResource> resources = new ArrayList<>();
      for (var result : executor.invokeAll(tasks)) {
        resources.add(result.get());
      }

      assertThat(resources)
          .extracting(resource -> resource.properties().get("service.port"))
          .doesNotHaveDuplicates();
      resources.forEach(EnvironmentResource::cleanup);
    }
  }

  @Test
  void parallelSharedLeasesStartOnceAndLastLeaseStops() throws Exception {
    ContainerLifecycleCoordinator coordinator = new ContainerLifecycleCoordinator();
    FakeContainer container = FakeContainer.success(21_001);
    AtomicInteger factories = new AtomicInteger();
    EnvironmentRequest request = request("shared", ContainerLifecycle.SHARED);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var tasks =
          java.util.stream.IntStream.range(0, 40)
              .<java.util.concurrent.Callable<EnvironmentResource>>mapToObj(
                  index ->
                      () ->
                          coordinator.acquire(
                              request,
                              DATABASE,
                              () -> {
                                factories.incrementAndGet();
                                return container;
                              }))
              .toList();
      List<EnvironmentResource> leases = new ArrayList<>();
      for (var future : executor.invokeAll(tasks)) {
        leases.add(future.get());
      }

      assertThat(factories).hasValue(1);
      assertThat(container.starts).hasValue(1);
      leases.subList(0, leases.size() - 1).forEach(EnvironmentResource::cleanup);
      assertThat(container.stops).hasValue(0);
      leases.getLast().cleanup();
      leases.getLast().cleanup();
      assertThat(container.stops).hasValue(1);
      assertThat(coordinator.sharedContainerCount()).isZero();
    }
  }

  @Test
  void failedInitializationStopsContainerAndDoesNotLeakSharedState() {
    ContainerLifecycleCoordinator coordinator = new ContainerLifecycleCoordinator();
    FakeContainer container = FakeContainer.failing();

    assertThatThrownBy(
            () ->
                coordinator.acquire(
                    request("failure", ContainerLifecycle.SHARED), DATABASE, () -> container))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("startup failed");

    assertThat(container.stops).hasValue(1);
    assertThat(coordinator.sharedContainerCount()).isZero();
  }

  @Test
  void failedDynamicPropertyResolutionStopsInitializedContainer() {
    ContainerLifecycleCoordinator coordinator = new ContainerLifecycleCoordinator();
    FakeContainer container = FakeContainer.success(21_004);
    container.failProperties = true;

    assertThatThrownBy(
            () ->
                coordinator.acquire(
                    request("property-failure", ContainerLifecycle.SHARED),
                    DATABASE,
                    () -> container))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("property resolution failed");

    assertThat(container.starts).hasValue(1);
    assertThat(container.stops).hasValue(1);
    assertThat(coordinator.sharedContainerCount()).isZero();
  }

  @Test
  void cancellationDuringInitializationStopsContainerAndPreservesInterrupt() {
    ContainerLifecycleCoordinator coordinator = new ContainerLifecycleCoordinator();
    FakeContainer container = FakeContainer.success(21_002);
    container.interruptOnStart = true;

    assertThatThrownBy(
            () ->
                coordinator.acquire(
                    request("cancelled", ContainerLifecycle.ISOLATED), DATABASE, () -> container))
        .isInstanceOf(ContainerCancellationException.class);

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    assertThat(container.stops).hasValue(1);
    Thread.interrupted();
  }

  @Test
  void invalidLifecycleIsActionable() {
    EnvironmentRequest request =
        new EnvironmentRequest(
            "bad",
            DATABASE,
            EnvironmentMode.CONTAINER,
            Set.of(),
            Map.of(ContainerLifecycle.PROPERTY, "suite"),
            Duration.ofSeconds(1));

    assertThatThrownBy(
            () ->
                new ContainerLifecycleCoordinator()
                    .acquire(request, DATABASE, () -> FakeContainer.success(21_003)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("isolated")
        .hasMessageContaining("shared");
  }

  private static EnvironmentRequest request(String name, ContainerLifecycle lifecycle) {
    return new EnvironmentRequest(
        name,
        DATABASE,
        EnvironmentMode.CONTAINER,
        Set.of(),
        Map.of(ContainerLifecycle.PROPERTY, lifecycle.name()),
        Duration.ofSeconds(2));
  }

  private static final class FakeContainer implements ManagedContainer {
    private final int port;
    private final boolean fail;
    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicInteger stops = new AtomicInteger();
    private volatile boolean running;
    private boolean interruptOnStart;
    private boolean failProperties;

    private FakeContainer(int port, boolean fail) {
      this.port = port;
      this.fail = fail;
    }

    static FakeContainer success(int port) {
      return new FakeContainer(port, false);
    }

    static FakeContainer failing() {
      return new FakeContainer(0, true);
    }

    @Override
    public void start() {
      starts.incrementAndGet();
      if (fail) throw new IllegalStateException("startup failed");
      running = true;
      if (interruptOnStart) Thread.currentThread().interrupt();
    }

    @Override
    public void stop() {
      stops.incrementAndGet();
      running = false;
    }

    @Override
    public boolean isRunning() {
      return running;
    }

    @Override
    public Map<String, String> properties() {
      if (failProperties) throw new IllegalStateException("property resolution failed");
      return Map.of("service.port", Integer.toString(port));
    }

    @Override
    public String logs() {
      return "ready";
    }
  }
}
