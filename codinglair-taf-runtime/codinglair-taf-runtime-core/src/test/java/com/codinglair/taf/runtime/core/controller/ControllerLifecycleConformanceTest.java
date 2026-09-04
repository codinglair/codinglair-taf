package com.codinglair.taf.runtime.core.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ControllerLifecycleConformanceTest {
  @Test
  void initializesLazilyAndOnlyOnceUnderConcurrency() throws Exception {
    TestSession session = TestSession.create();
    RecordingController controller = new RecordingController("api", new ArrayList<>());
    session.getControllerRegistry().register(RecordingController.class, "api", controller);
    assertThat(controller.initializations).hasValue(0);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var ready = new CountDownLatch(8);
      var start = new CountDownLatch(1);
      for (int i = 0; i < 8; i++)
        executor.submit(
            () -> {
              ready.countDown();
              start.await();
              session.getController(RecordingController.class, "api");
              return null;
            });
      ready.await();
      start.countDown();
    }
    assertThat(controller.initializations).hasValue(1);
    assertThat(controller.state()).isEqualTo(ControllerState.READY);
  }

  @Test
  void failedInitializationClosesPartialResourcesAndRetainsDiagnostics() {
    TestSession session = TestSession.create();
    RecordingController controller = new RecordingController("broken", new ArrayList<>());
    controller.initializationFailure = new IllegalStateException("safe diagnostic");
    controller.closeFailure = new IllegalArgumentException("cleanup diagnostic");
    session.getControllerRegistry().register(RecordingController.class, "broken", controller);
    assertThatThrownBy(() -> session.getController(RecordingController.class, "broken"))
        .hasMessage("safe diagnostic")
        .satisfies(f -> assertThat(f.getSuppressed()).hasSize(1));
    assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
  }

  @Test
  void closesControllersThenResourcesInReverseOrderAndPreservesFailures() {
    List<String> order = new ArrayList<>();
    TestSession session = TestSession.create();
    RecordingController first = new RecordingController("first", order);
    RecordingController second = new RecordingController("second", order);
    first.closeFailure = new IllegalStateException("first");
    second.closeFailure = new IllegalArgumentException("second");
    session.getControllerRegistry().register(RecordingController.class, "first", first);
    session.getControllerRegistry().register(RecordingController.class, "second", second);
    session.getController(RecordingController.class, "first");
    session.getController(RecordingController.class, "second");
    session.addCleanupListener(id -> order.add("resource-one"));
    session.addCleanupListener(id -> order.add("resource-two"));
    assertThatThrownBy(session::close)
        .isInstanceOf(IllegalArgumentException.class)
        .satisfies(f -> assertThat(f.getSuppressed()).hasSize(1));
    session.close();
    assertThat(order).containsExactly("second", "first", "resource-two", "resource-one");
  }

  @Test
  void parallelSessionsRemainIsolated() {
    TestSession one = TestSession.create();
    TestSession two = TestSession.create();
    var first = new RecordingController("same", new ArrayList<>());
    var second = new RecordingController("same", new ArrayList<>());
    one.getControllerRegistry().register(RecordingController.class, "same", first);
    two.getControllerRegistry().register(RecordingController.class, "same", second);
    assertThat(one.getController(RecordingController.class, "same"))
        .isSameAs(first)
        .isNotSameAs(two.getController(RecordingController.class, "same"));
  }

  static final class RecordingController implements TestController {
    final String name;
    final List<String> closeOrder;
    final AtomicInteger initializations = new AtomicInteger();
    final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
    RuntimeException initializationFailure;
    RuntimeException closeFailure;

    RecordingController(String name, List<String> closeOrder) {
      this.name = name;
      this.closeOrder = closeOrder;
    }

    public ControllerIdentity identity() {
      return new ControllerIdentity(RecordingController.class, name);
    }

    public ControllerState state() {
      return state.get();
    }

    public void initialize(ControllerContext context) {
      state.set(ControllerState.INITIALIZING);
      initializations.incrementAndGet();
      if (initializationFailure != null) {
        state.set(ControllerState.FAILED);
        throw initializationFailure;
      }
      state.set(ControllerState.READY);
    }

    public HealthResult health() {
      return HealthResult.unknown("test");
    }

    public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
      return Stream.empty();
    }

    public void close() {
      if (state.getAndSet(ControllerState.CLOSED) != ControllerState.CLOSED) {
        closeOrder.add(name);
        if (closeFailure != null) throw closeFailure;
      }
    }
  }
}
