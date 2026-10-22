package com.codinglair.taf.runtime.core.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TestSessionLifecycleTest {
  @Test
  void opensResolvesAndClosesExactlyOneSession() {
    TestSessionLifecycle lifecycle = new TestSessionLifecycle(TestSession::create);
    InvocationDescriptor invocation = descriptor("one");

    TestSession session = lifecycle.open(invocation);

    assertThat(lifecycle.require()).isSameAs(session);
    assertThat(lifecycle.activeOwnerships()).containsKey("one");
    lifecycle.close(invocation, InvocationOutcome.passed());
    assertThat(lifecycle.find()).isEmpty();
    assertThat(lifecycle.activeOwnerships()).isEmpty();
    assertThatThrownBy(() -> lifecycle.close(invocation, InvocationOutcome.passed()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void duplicateInvocationAndNestedOwnershipFailDeterministically() {
    TestSessionLifecycle lifecycle = new TestSessionLifecycle(TestSession::create);
    InvocationDescriptor first = descriptor("duplicate");
    lifecycle.open(first);

    assertThatThrownBy(() -> lifecycle.open(first))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already owns");
    assertThatThrownBy(() -> lifecycle.open(descriptor("nested")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already owns");
    lifecycle.close(first, InvocationOutcome.passed());
  }

  @Test
  void testFailureRemainsPrimaryAndCleanupFailuresAreSuppressedInReverseOrder() {
    TestSessionLifecycle lifecycle = new TestSessionLifecycle(TestSession::create);
    InvocationDescriptor invocation = descriptor("failure");
    List<String> cleanupOrder = new ArrayList<>();
    TestSession session = lifecycle.open(invocation);
    session.addCleanupListener(
        ignored -> {
          cleanupOrder.add("first");
          throw new IllegalStateException("first cleanup");
        });
    session.addCleanupListener(
        ignored -> {
          cleanupOrder.add("second");
          throw new IllegalArgumentException("second cleanup");
        });
    AssertionError testFailure = new AssertionError("test failed");

    lifecycle.close(invocation, InvocationOutcome.failed(testFailure));
    assertThat(testFailure.getSuppressed()).hasSize(1);
    assertThat(cleanupOrder).containsExactly("second", "first");
    assertThat(lifecycle.find()).isEmpty();
    assertThat(lifecycle.activeOwnerships()).isEmpty();
  }

  @Test
  void setupFailureDoesNotLeaveOwnershipReservation() {
    TestSessionLifecycle lifecycle =
        new TestSessionLifecycle(
            () -> {
              throw new IllegalStateException("setup");
            });
    assertThatThrownBy(() -> lifecycle.open(descriptor("setup"))).hasMessage("setup");
    assertThat(lifecycle.find()).isEmpty();
    assertThat(lifecycle.activeOwnerships()).isEmpty();
  }

  @Test
  void cancellationPreservesInterruptFailureAndStillCleansUp() {
    TestSessionLifecycle lifecycle = new TestSessionLifecycle(TestSession::create);
    InvocationDescriptor invocation = descriptor("cancelled");
    List<String> cleaned = new ArrayList<>();
    lifecycle.open(invocation).addCleanupListener(cleaned::add);
    InterruptedException cancellation = new InterruptedException("cancelled");

    lifecycle.close(invocation, InvocationOutcome.cancelled(cancellation));

    assertThat(cleaned).hasSize(1);
    assertThat(cancellation.getSuppressed()).isEmpty();
    assertThat(lifecycle.find()).isEmpty();
    assertThat(lifecycle.activeOwnerships()).isEmpty();
  }

  @Test
  void parallelInvocationsAreIsolatedAndLeaveNoLeaks() throws Exception {
    TestSessionLifecycle lifecycle = new TestSessionLifecycle(TestSession::create);
    Set<String> sessions = ConcurrentHashMap.newKeySet();
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<java.util.concurrent.Callable<Void>> tasks =
          java.util.stream.IntStream.range(0, 32)
              .mapToObj(
                  index ->
                      (java.util.concurrent.Callable<Void>)
                          () -> {
                            InvocationDescriptor invocation = descriptor("parallel-" + index);
                            TestSession opened = lifecycle.open(invocation);
                            assertThat(lifecycle.require()).isSameAs(opened);
                            sessions.add(opened.getSessionId());
                            lifecycle.close(invocation, InvocationOutcome.passed());
                            assertThat(lifecycle.find()).isEmpty();
                            return null;
                          })
              .toList();
      for (var future : executor.invokeAll(tasks)) future.get();
    }
    assertThat(sessions).hasSize(32);
    assertThat(lifecycle.activeOwnerships()).isEmpty();
  }

  @Test
  void retainedControllerRejectsClosedAndWrongInvocationBeforeSideEffects() {
    TestSessionLifecycle lifecycle = new TestSessionLifecycle(TestSession::create);
    InvocationDescriptor first = descriptor("first");
    TestSession firstSession = lifecycle.open(first);
    RecordingCapability implementation = new RecordingCapability("capability");
    firstSession.getControllerRegistry().register(Capability.class, "capability", implementation);
    Capability retained = firstSession.getController(Capability.class, "capability");

    retained.execute();
    lifecycle.close(first, InvocationOutcome.passed());
    assertThatThrownBy(retained::execute)
        .isInstanceOf(LifecycleViolationException.class)
        .hasMessageContaining(LifecycleViolationException.ERROR_CODE);

    InvocationDescriptor second = descriptor("second");
    lifecycle.open(second);
    assertThatThrownBy(retained::execute)
        .isInstanceOf(LifecycleViolationException.class)
        .hasMessageContaining("capability belongs to session");
    assertThat(implementation.sideEffects).hasValue(1);
    lifecycle.close(second, InvocationOutcome.passed());
  }

  private static InvocationDescriptor descriptor(String id) {
    return new InvocationDescriptor(id, id, "test-owner");
  }

  private interface Capability extends TestController {
    void execute();
  }

  private static final class RecordingCapability implements Capability {
    private final String name;
    private final AtomicInteger sideEffects = new AtomicInteger();
    private ControllerState state = ControllerState.NEW;

    private RecordingCapability(String name) {
      this.name = name;
    }

    @Override
    public void execute() {
      sideEffects.incrementAndGet();
    }

    @Override
    public ControllerIdentity identity() {
      return new ControllerIdentity(Capability.class, name);
    }

    @Override
    public ControllerState state() {
      return state;
    }

    @Override
    public void initialize(ControllerContext context) {
      state = ControllerState.READY;
    }

    @Override
    public HealthResult health() {
      return HealthResult.unknown("test");
    }

    @Override
    public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
      return Stream.empty();
    }

    @Override
    public void close() {
      state = ControllerState.CLOSED;
    }
  }
}
