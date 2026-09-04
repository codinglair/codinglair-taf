package com.codinglair.taf.runtime.core.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerRegistry;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.controller.TestController;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SessionAwareAccessorTest {
  @Test
  void accessOutsideFrameworkLifecycleFailsWithoutCreatingSession() {
    AtomicInteger creations = new AtomicInteger();
    TestSessionLifecycle lifecycle =
        new TestSessionLifecycle(
            () -> {
              creations.incrementAndGet();
              return session("unexpected");
            });
    SessionAwareAccessor accessor = new DefaultSessionAwareAccessor(lifecycle);

    assertThatIllegalStateException()
        .isThrownBy(accessor::session)
        .withMessageContaining("No TestSession")
        .withMessageContaining("TestNG method or Cucumber scenario");
    assertThat(creations).hasValue(0);
    assertThat(lifecycle.activeOwnerships()).isEmpty();
  }

  @Test
  void singletonFactoryResolvesFreshInvocationObjectsInsteadOfRetainingControllers() {
    AtomicInteger sequence = new AtomicInteger();
    TestSessionLifecycle lifecycle =
        new TestSessionLifecycle(() -> session("session-" + sequence.incrementAndGet()));
    SessionAwareAccessor accessor = new DefaultSessionAwareAccessor(lifecycle);
    PageFactory singletonFactory = new PageFactory(accessor);

    Page first = invoke(lifecycle, singletonFactory, "one");
    Page second = invoke(lifecycle, singletonFactory, "two");

    assertThat(first.controller()).isNotSameAs(second.controller());
    assertThat(first.controller().sessionId()).isEqualTo("session-1");
    assertThat(second.controller().sessionId()).isEqualTo("session-2");
    assertThat(sequence).hasValue(2);
    assertThat(lifecycle.activeOwnerships()).isEmpty();
  }

  @Test
  void singletonFactoryResolvesIsolatedControllersAcrossParallelInvocations() throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    TestSessionLifecycle lifecycle =
        new TestSessionLifecycle(() -> session("parallel-" + sequence.incrementAndGet()));
    PageFactory singletonFactory = new PageFactory(new DefaultSessionAwareAccessor(lifecycle));
    Set<String> sessionIds = ConcurrentHashMap.newKeySet();
    Set<SampleController> controllers = ConcurrentHashMap.newKeySet();

    try (var executor = Executors.newFixedThreadPool(8)) {
      var tasks =
          IntStream.range(0, 32)
              .mapToObj(
                  index ->
                      (java.util.concurrent.Callable<Void>)
                          () -> {
                            Page page = invoke(lifecycle, singletonFactory, "parallel-" + index);
                            sessionIds.add(page.controller().sessionId());
                            controllers.add(page.controller());
                            return null;
                          })
              .toList();
      for (var result : executor.invokeAll(tasks)) {
        result.get();
      }
    }

    assertThat(sessionIds).hasSize(32);
    assertThat(controllers).hasSize(32);
    assertThat(sequence).hasValue(32);
    assertThat(lifecycle.activeOwnerships()).isEmpty();
  }

  private static Page invoke(TestSessionLifecycle lifecycle, PageFactory factory, String id) {
    InvocationDescriptor descriptor = new InvocationDescriptor(id, id, "test");
    lifecycle.open(descriptor);
    try {
      return factory.create();
    } finally {
      lifecycle.close(descriptor, InvocationOutcome.passed());
    }
  }

  private static TestSession session(String id) {
    ControllerRegistry registry = new ControllerRegistry();
    registry.register(SampleController.class, "sample", new SampleController());
    return TestSession.create(id, registry, List.of());
  }

  private record Page(SampleController controller) {}

  private static final class PageFactory {
    private final SessionAwareAccessor accessor;

    private PageFactory(SessionAwareAccessor accessor) {
      this.accessor = accessor;
    }

    private Page create() {
      return new Page(accessor.controller(SampleController.class, "sample"));
    }
  }

  private static final class SampleController implements TestController {
    private volatile ControllerState state = ControllerState.NEW;
    private volatile String sessionId;

    @Override
    public ControllerIdentity identity() {
      return new ControllerIdentity(SampleController.class, "sample");
    }

    @Override
    public ControllerState state() {
      return state;
    }

    @Override
    public void initialize(ControllerContext context) {
      sessionId = context.sessionId();
      state = ControllerState.READY;
    }

    @Override
    public HealthResult health() {
      return new HealthResult(HealthResult.Status.HEALTHY, "ready", java.util.Map.of());
    }

    @Override
    public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
      return Stream.empty();
    }

    @Override
    public void close() {
      state = ControllerState.CLOSED;
    }

    private String sessionId() {
      return sessionId;
    }
  }
}
