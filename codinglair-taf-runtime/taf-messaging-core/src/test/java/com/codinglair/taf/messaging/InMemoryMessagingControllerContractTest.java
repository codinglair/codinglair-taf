package com.codinglair.taf.messaging;

import com.codinglair.taf.messaging.contract.MessagingControllerContract;
import com.codinglair.taf.messaging.contract.MessagingControllerContractHarness;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;

@DisplayName("In-memory messaging adapter")
class InMemoryMessagingControllerContractTest extends MessagingControllerContract {
  @Override
  protected MessagingControllerContractHarness createHarness() {
    return new InMemoryHarness();
  }

  private static final class InMemoryHarness implements MessagingControllerContractHarness {
    private final InMemoryMessagingController controller = new InMemoryMessagingController();

    private InMemoryHarness() {
      controller.initialize(
          new ControllerContext(
              "messaging-contract",
              EnvironmentAccess.unavailable(),
              new ArtifactCollector(
                  TafTest.of("messaging", getClass().getName()), "messaging-contract", "test")));
    }

    @Override
    public MessagingController controller() {
      return controller;
    }

    @Override
    public void awaitConsumeStarted(Duration timeout) throws InterruptedException {
      if (!controller.consumeStarted.await(timeout.toMillis(), TimeUnit.MILLISECONDS))
        throw new IllegalStateException("consume did not start within " + timeout);
    }

    @Override
    public boolean isClosed() {
      return controller.state() == ControllerState.CLOSED;
    }

    @Override
    public void close() {
      controller.close();
    }
  }

  private static final class InMemoryMessagingController implements MessagingController {
    private final ControllerIdentity identity =
        new ControllerIdentity(MessagingController.class, "in-memory");
    private final AtomicReference<ControllerState> state =
        new AtomicReference<>(ControllerState.NEW);
    private final Map<String, DestinationBuffer> messages = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final CountDownLatch consumeStarted = new CountDownLatch(1);

    @Override
    public ControllerIdentity identity() {
      return identity;
    }

    @Override
    public ControllerState state() {
      return state.get();
    }

    @Override
    public void initialize(ControllerContext context) {
      if (!state.compareAndSet(ControllerState.NEW, ControllerState.READY))
        throw new IllegalStateException("controller already initialized");
    }

    @Override
    public HealthResult health() {
      return state.get() == ControllerState.READY
          ? new HealthResult(HealthResult.Status.HEALTHY, "in-memory adapter ready", Map.of())
          : HealthResult.unknown("in-memory adapter is " + state.get());
    }

    @Override
    public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
      return Stream.empty();
    }

    @Override
    public MessageRecord publish(String destination, MessageEnvelope message) {
      ensureReady();
      if (destination == null || destination.isBlank())
        throw new IllegalArgumentException("destination must not be blank");
      MessageRecord record =
          new MessageRecord(
              message,
              Instant.now(),
              Map.of("provider", "in-memory", "sequence", sequence.incrementAndGet()));
      messages.computeIfAbsent(destination, ignored -> new DestinationBuffer()).add(record);
      return record;
    }

    @Override
    public ConsumptionResult consume(MessageQuery query) throws InterruptedException {
      ensureReady();
      consumeStarted.countDown();
      long started = System.nanoTime();
      long deadline = started + query.timeout().toNanos();
      DestinationBuffer buffer =
          messages.computeIfAbsent(query.destination(), ignored -> new DestinationBuffer());
      MessageRecord match = buffer.takeMatching(query.selector(), deadline);
      Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
      return match == null
          ? ConsumptionResult.noMatch(elapsed)
          : ConsumptionResult.matched(match, elapsed);
    }

    @Override
    public void close() {
      messages.clear();
      state.set(ControllerState.CLOSED);
    }

    private void ensureReady() {
      if (state.get() != ControllerState.READY)
        throw new IllegalStateException("controller is not ready: " + state.get());
    }

    private static final class DestinationBuffer {
      private final ArrayList<MessageRecord> records = new ArrayList<>();

      synchronized void add(MessageRecord record) {
        records.add(record);
        notifyAll();
      }

      synchronized MessageRecord takeMatching(MessageSelector selector, long deadline)
          throws InterruptedException {
        while (true) {
          for (int index = 0; index < records.size(); index++) {
            MessageRecord candidate = records.get(index);
            if (selector.matches(candidate)) return records.remove(index);
          }
          long remaining = deadline - System.nanoTime();
          if (remaining <= 0) return null;
          TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
      }
    }
  }
}
