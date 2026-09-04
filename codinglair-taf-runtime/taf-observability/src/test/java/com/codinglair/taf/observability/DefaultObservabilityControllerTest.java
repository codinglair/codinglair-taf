package com.codinglair.taf.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.runtime.core.context.CorrelationContext;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.RedactionPipeline;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Default observability controller")
class DefaultObservabilityControllerTest {
  private final List<DefaultObservabilityController> controllers = new ArrayList<>();

  @AfterEach
  void closeControllersAndClearInterrupt() {
    controllers.forEach(DefaultObservabilityController::close);
    Thread.interrupted();
  }

  @Nested
  @DisplayName("Queries")
  class Queries {
    @ParameterizedTest(name = "queries {0} telemetry")
    @EnumSource(TelemetryType.class)
    @DisplayName("Queries every supported signal")
    void queriesEverySupportedSignal(TelemetryType type) {
      Instant now = Instant.now();
      TelemetryRecord record = new TelemetryRecord(now, Map.of("service", "orders"), "healthy");
      DefaultObservabilityController controller = ready(provider(type, _ -> result(type, record)));

      TelemetryQueryResult actual = controller.query(query(type, now));

      assertThat(actual.type()).isEqualTo(type);
      assertThat(actual.records()).containsExactly(record);
      assertThat(actual.hasData()).isTrue();
    }

    @Test
    @DisplayName("Propagates existing correlation identifiers to the provider")
    void propagatesCorrelationIdentifiers() {
      Instant now = Instant.now();
      CorrelationContext correlation = CorrelationContext.create("trace-17", "span-4", "session-9");
      var observed = new ConcurrentLinkedQueue<Map<String, String>>();
      DefaultObservabilityController controller =
          ready(
              provider(
                  TelemetryType.TRACE,
                  query -> {
                    observed.add(query.correlation());
                    return empty(TelemetryType.TRACE);
                  }));

      controller.query(
          TelemetryQuery.traces("service=checkout", now.minusSeconds(1), now, 10)
              .correlatedBy(correlation));

      assertThat(observed)
          .containsExactly(
              Map.of("traceId", "trace-17", "spanId", "span-4", "sessionId", "session-9"));
    }

    @Test
    @DisplayName("Returns an empty result when telemetry is available but absent")
    void distinguishesAbsentTelemetry() {
      Instant now = Instant.now();
      DefaultObservabilityController controller =
          ready(provider(TelemetryType.LOG, _ -> empty(TelemetryType.LOG)));

      TelemetryQueryResult actual =
          controller.query(TelemetryQuery.logs("missing", now.minusSeconds(1), now, 10));

      assertThat(actual.hasData()).isFalse();
      assertThat(actual.matchedCount()).isZero();
    }

    @Test
    @DisplayName("Classifies provider failures as environment outages")
    void classifiesProviderOutage() {
      Instant now = Instant.now();
      DefaultObservabilityController controller =
          ready(
              provider(
                  TelemetryType.METRIC,
                  _ -> {
                    throw new IllegalStateException("raw endpoint detail");
                  }));

      assertThatThrownBy(
              () -> controller.query(TelemetryQuery.metrics("up", now.minusSeconds(1), now, 10)))
          .isInstanceOf(ProviderUnavailableException.class)
          .hasMessageContaining("stub-METRIC")
          .hasMessageNotContaining("raw endpoint detail")
          .extracting("telemetryType")
          .isEqualTo(TelemetryType.METRIC);
    }

    @Test
    @DisplayName("Redacts and summarizes oversized provider responses")
    void safelySummarizesLargeResponses() {
      Instant now = Instant.now();
      List<TelemetryRecord> records =
          Stream.of("one token=canary", "two token=canary", "three token=canary")
              .map(
                  payload ->
                      new TelemetryRecord(now, Map.of("authorization", "token=canary"), payload))
              .toList();
      DefaultObservabilityController controller =
          ready(
              provider(
                  TelemetryType.LOG,
                  _ ->
                      new TelemetryQueryResult(
                          TelemetryType.LOG, records, 3, false, "token=canary")),
              new ObservabilityLimits(Duration.ofMinutes(1), 2, 8));

      TelemetryQueryResult actual =
          controller.query(TelemetryQuery.logs("all", now.minusSeconds(1), now, 2));

      assertThat(actual.records()).hasSize(2);
      assertThat(actual.truncated()).isTrue();
      assertThat(actual.summary()).isEqualTo("LOG matched=3, returned=2, truncated=true");
      assertThat(actual.toString()).doesNotContain("canary");
      assertThat(actual.records())
          .allSatisfy(record -> assertThat(record.payload()).contains("****"));
    }

    @Test
    @DisplayName("Rejects queries outside configured time and cardinality bounds")
    void rejectsOutOfBoundsQueries() {
      Instant now = Instant.now();
      DefaultObservabilityController controller =
          ready(
              provider(TelemetryType.LOG, _ -> empty(TelemetryType.LOG)),
              new ObservabilityLimits(Duration.ofSeconds(5), 2, 100));

      assertThatThrownBy(
              () -> controller.query(TelemetryQuery.logs("all", now.minusSeconds(6), now, 2)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("window");
      assertThatThrownBy(
              () -> controller.query(TelemetryQuery.logs("all", now.minusSeconds(1), now, 3)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("limit");
    }
  }

  @Nested
  @DisplayName("Eventual assertions")
  class EventualAssertions {
    @Test
    @DisplayName("Matches telemetry that becomes available")
    void matchesEventually() {
      Instant now = Instant.now();
      AtomicInteger calls = new AtomicInteger();
      TelemetryRecord record = new TelemetryRecord(now, Map.of(), "ready");
      DefaultObservabilityController controller =
          ready(
              provider(
                  TelemetryType.TRACE,
                  _ ->
                      calls.incrementAndGet() < 3
                          ? empty(TelemetryType.TRACE)
                          : result(TelemetryType.TRACE, record)));

      TelemetryAssertionResult actual =
          assertTimeoutPreemptively(
              Duration.ofSeconds(1),
              () ->
                  controller.assertEventually(
                      TelemetryQuery.traces("checkout", now.minusSeconds(1), now, 10),
                      TelemetryQueryResult::hasData,
                      Duration.ofMillis(300),
                      Duration.ofMillis(5)));

      assertThat(actual.matched()).isTrue();
      assertThat(actual.pollCount()).isEqualTo(3);
    }

    @ParameterizedTest(name = "times out for absent {0} telemetry")
    @EnumSource(TelemetryType.class)
    @DisplayName("Times out for every telemetry signal when data remains absent")
    void timesOutWhenAbsent(TelemetryType type) {
      Instant now = Instant.now();
      DefaultObservabilityController controller = ready(provider(type, _ -> empty(type)));

      TelemetryAssertionResult actual =
          assertTimeoutPreemptively(
              Duration.ofSeconds(1),
              () ->
                  controller.assertEventually(
                      query(type, now),
                      TelemetryQueryResult::hasData,
                      Duration.ofMillis(30),
                      Duration.ofMillis(5)));

      assertThat(actual.status()).isEqualTo(TelemetryAssertionResult.Status.TIMED_OUT);
      assertThat(actual.lastResult().hasData()).isFalse();
      assertThat(actual.pollCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Preserves interruption and stops polling")
    void preservesInterruption() {
      Instant now = Instant.now();
      DefaultObservabilityController controller =
          ready(provider(TelemetryType.LOG, _ -> empty(TelemetryType.LOG)));
      Thread.currentThread().interrupt();

      assertThatThrownBy(
              () ->
                  controller.assertEventually(
                      TelemetryQuery.logs("later", now.minusSeconds(1), now, 10),
                      TelemetryQueryResult::hasData,
                      Duration.ofSeconds(1),
                      Duration.ofMillis(10)))
          .isInstanceOf(ObservabilityAssertionException.class)
          .hasMessageContaining("interrupted after 1 polls");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
  }

  @Nested
  @DisplayName("Lifecycle and concurrency")
  class LifecycleAndConcurrency {
    @Test
    @DisplayName("Requires initialization and closes idempotently")
    void lifecycleIsEnforced() {
      Instant now = Instant.now();
      DefaultObservabilityController controller =
          tracked(
              provider(TelemetryType.LOG, _ -> empty(TelemetryType.LOG)),
              ObservabilityLimits.DEFAULTS);

      assertThatThrownBy(
              () -> controller.query(TelemetryQuery.logs("all", now.minusSeconds(1), now, 1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not READY");
      controller.initialize(context());
      controller.close();
      controller.close();
      assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
    }

    @Test
    @DisplayName("Supports concurrent queries without shared result state")
    void supportsConcurrentQueries() throws Exception {
      Instant now = Instant.now();
      AtomicInteger calls = new AtomicInteger();
      DefaultObservabilityController controller =
          ready(
              provider(
                  TelemetryType.LOG,
                  _ -> {
                    int call = calls.incrementAndGet();
                    return result(
                        TelemetryType.LOG, new TelemetryRecord(now, Map.of(), "call-" + call));
                  }));

      try (var executor = Executors.newFixedThreadPool(8)) {
        var futures =
            java.util.stream.IntStream.range(0, 32)
                .mapToObj(
                    _ ->
                        executor.submit(
                            () ->
                                controller.query(
                                    TelemetryQuery.logs("all", now.minusSeconds(1), now, 1))))
                .toList();
        for (var future : futures) {
          assertThat(future.get().records()).hasSize(1);
        }
      }
      assertThat(calls).hasValue(32);
    }
  }

  private DefaultObservabilityController ready(ObservabilityProvider provider) {
    return ready(provider, ObservabilityLimits.DEFAULTS);
  }

  private DefaultObservabilityController ready(
      ObservabilityProvider provider, ObservabilityLimits limits) {
    DefaultObservabilityController controller = tracked(provider, limits);
    controller.initialize(context());
    return controller;
  }

  private DefaultObservabilityController tracked(
      ObservabilityProvider provider, ObservabilityLimits limits) {
    DefaultObservabilityController controller =
        new DefaultObservabilityController(
            "primary", List.of(provider), new RedactionPipeline(), limits);
    controllers.add(controller);
    return controller;
  }

  private static ControllerContext context() {
    return new ControllerContext(
        "session",
        EnvironmentAccess.unavailable(),
        new ArtifactCollector(TafTest.of("observability", "test"), "session", "test"));
  }

  private static TelemetryQuery query(TelemetryType type, Instant now) {
    return switch (type) {
      case LOG -> TelemetryQuery.logs("service=orders", now.minusSeconds(1), now.plusMillis(1), 10);
      case METRIC ->
          TelemetryQuery.metrics("requests_total", now.minusSeconds(1), now.plusMillis(1), 10);
      case TRACE ->
          TelemetryQuery.traces("service=orders", now.minusSeconds(1), now.plusMillis(1), 10);
    };
  }

  private static TelemetryQueryResult result(TelemetryType type, TelemetryRecord record) {
    return new TelemetryQueryResult(type, List.of(record), 1, false, "provider summary");
  }

  private static TelemetryQueryResult empty(TelemetryType type) {
    return new TelemetryQueryResult(type, List.of(), 0, false, "provider summary");
  }

  private static ObservabilityProvider provider(
      TelemetryType type, java.util.function.Function<TelemetryQuery, TelemetryQueryResult> query) {
    return new ObservabilityProvider() {
      @Override
      public String name() {
        return "stub-" + type;
      }

      @Override
      public TelemetryType type() {
        return type;
      }

      @Override
      public TelemetryQueryResult query(TelemetryQuery request) {
        return query.apply(request);
      }
    };
  }
}
