package com.codinglair.taf.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.messaging.ConsumptionResult;
import com.codinglair.taf.messaging.Correlation;
import com.codinglair.taf.messaging.MessageEnvelope;
import com.codinglair.taf.messaging.MessageQuery;
import com.codinglair.taf.messaging.MessageSelector;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reusable contract suite for Kafka, RabbitMQ, JMS, and other messaging adapters. */
@DisplayName("Messaging controller adapter contract")
public abstract class MessagingControllerContract {
  private MessagingControllerContractHarness harness;

  protected abstract MessagingControllerContractHarness createHarness();

  @BeforeEach
  void createController() {
    harness = createHarness();
  }

  @AfterEach
  void closeController() {
    if (harness != null) {
      harness.close();
      harness = null;
    }
  }

  @Nested
  @DisplayName("Publish and consume")
  class PublishAndConsume {
    @Test
    @DisplayName("publishes and consumes payload, headers, correlation, and native metadata")
    void roundTrip() throws Exception {
      Correlation correlation = new Correlation("trace-id", "trace-123");
      MessageEnvelope envelope =
          new MessageEnvelope(
              "hello".getBytes(StandardCharsets.UTF_8),
              Map.of("content-type", "text/plain"),
              Optional.of(correlation));

      var published = harness.controller().publish("orders", envelope);
      ConsumptionResult result =
          harness
              .controller()
              .consume(
                  new MessageQuery(
                      "orders",
                      new MessageSelector(
                          Optional.of(correlation),
                          record -> record.envelope().headers().containsKey("content-type")),
                      Duration.ofSeconds(1)));

      assertThat(result.status()).isEqualTo(ConsumptionResult.Status.MATCHED);
      assertThat(result.record()).contains(published);
      assertThat(result.record().orElseThrow().nativeMetadata()).isNotEmpty();
    }

    @Test
    @DisplayName("isolates concurrent correlation matches")
    void concurrentCorrelation() throws Exception {
      Correlation first = new Correlation("id", "first");
      Correlation second = new Correlation("id", "second");
      harness.controller().publish("events", envelope(first));
      harness.controller().publish("events", envelope(second));
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var firstResult =
            executor.submit(() -> harness.controller().consume(query("events", first)));
        var secondResult =
            executor.submit(() -> harness.controller().consume(query("events", second)));
        assertThat(firstResult.get().record().orElseThrow().envelope().correlation())
            .contains(first);
        assertThat(secondResult.get().record().orElseThrow().envelope().correlation())
            .contains(second);
      }
    }
  }

  @Nested
  @DisplayName("No-match semantics")
  class NoMatch {
    @Test
    @DisplayName("returns an explicit no-match only after the bounded timeout")
    void deterministicNoMatch() {
      Duration timeout = Duration.ofMillis(75);
      ConsumptionResult result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(1),
              () ->
                  harness
                      .controller()
                      .assertNoMatch(new MessageQuery("missing", MessageSelector.any(), timeout)));
      assertThat(result.status()).isEqualTo(ConsumptionResult.Status.NO_MATCH);
      assertThat(result.record()).isEmpty();
      assertThat(result.elapsed()).isGreaterThanOrEqualTo(timeout);
    }

    @Test
    @DisplayName("fails an explicit no-match assertion when a match exists")
    void unexpectedMatch() {
      harness.controller().publish("events", new MessageEnvelope(new byte[] {1}));
      assertThrows(
          AssertionError.class,
          () ->
              harness
                  .controller()
                  .assertNoMatch(
                      new MessageQuery("events", MessageSelector.any(), Duration.ofSeconds(1))));
    }
  }

  @Nested
  @DisplayName("Cancellation and cleanup")
  class CancellationAndCleanup {
    @Test
    @DisplayName("interrupts a blocked consume without converting cancellation into no-match")
    void cancellation() throws Exception {
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var future =
            executor.submit(
                () ->
                    harness
                        .controller()
                        .consume(
                            new MessageQuery(
                                "missing", MessageSelector.any(), Duration.ofSeconds(30))));
        harness.awaitConsumeStarted(Duration.ofSeconds(1));
        assertThat(future.cancel(true)).isTrue();
        assertThrows(CancellationException.class, () -> future.get(1, TimeUnit.SECONDS));
      }
    }

    @Test
    @DisplayName("cleanup is idempotent")
    void cleanup() {
      harness.close();
      harness.close();
      assertThat(harness.isClosed()).isTrue();
    }
  }

  private static MessageEnvelope envelope(Correlation correlation) {
    return new MessageEnvelope(new byte[] {1}, Map.of(), Optional.of(correlation));
  }

  private static MessageQuery query(String destination, Correlation correlation) {
    return new MessageQuery(
        destination, MessageSelector.correlated(correlation), Duration.ofSeconds(1));
  }
}
