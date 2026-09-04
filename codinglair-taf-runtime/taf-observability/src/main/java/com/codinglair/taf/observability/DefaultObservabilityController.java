package com.codinglair.taf.observability;

import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.reporting.RedactionService;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Thread-safe controller that applies bounds and redaction at the provider boundary. */
public final class DefaultObservabilityController implements ObservabilityController {
  private final String name;
  private final Map<TelemetryType, ObservabilityProvider> providers;
  private final RedactionService redaction;
  private final ObservabilityLimits limits;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);

  public DefaultObservabilityController(
      String name,
      List<? extends ObservabilityProvider> providers,
      RedactionService redaction,
      ObservabilityLimits limits) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Observability controller name must not be blank");
    }
    this.name = name;
    this.redaction = Objects.requireNonNull(redaction, "redaction");
    this.limits = Objects.requireNonNull(limits, "limits");
    EnumMap<TelemetryType, ObservabilityProvider> indexed = new EnumMap<>(TelemetryType.class);
    for (ObservabilityProvider provider : List.copyOf(providers)) {
      Objects.requireNonNull(provider, "provider");
      if (provider.name() == null || provider.name().isBlank()) {
        throw new IllegalArgumentException("Observability provider name must not be blank");
      }
      if (indexed.putIfAbsent(provider.type(), provider) != null) {
        throw new IllegalArgumentException("Multiple providers configured for " + provider.type());
      }
    }
    this.providers = Map.copyOf(indexed);
  }

  @Override
  public ControllerIdentity identity() {
    return new ControllerIdentity(ObservabilityController.class, name);
  }

  @Override
  public ControllerState state() {
    return state.get();
  }

  @Override
  public void initialize(ControllerContext context) {
    Objects.requireNonNull(context, "context");
    if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING)) {
      throw new IllegalStateException("Controller cannot initialize from " + state.get());
    }
    state.set(ControllerState.READY);
  }

  @Override
  public HealthResult health() {
    return state.get() == ControllerState.READY
        ? new HealthResult(
            HealthResult.Status.HEALTHY,
            "Observability controller is ready",
            Map.of("name", name, "signals", providers.keySet().toString()))
        : HealthResult.unknown("Observability controller is not ready: " + state.get());
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    return Stream.empty();
  }

  @Override
  public TelemetryQueryResult query(TelemetryQuery query) {
    requireReady();
    validateBounds(query);
    ObservabilityProvider provider = providers.get(query.type());
    if (provider == null) {
      throw new ProviderUnavailableException(
          "unconfigured-" + query.type().name().toLowerCase(),
          query.type(),
          "configure a compatible provider for this telemetry type",
          new IllegalStateException("Provider is not configured"));
    }
    try {
      TelemetryQueryResult result =
          Objects.requireNonNull(provider.query(query), "provider result");
      if (result.type() != query.type()) {
        throw new IllegalStateException("Provider returned the wrong telemetry type");
      }
      return sanitize(query, result);
    } catch (ProviderUnavailableException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ProviderUnavailableException(
          provider.name(),
          query.type(),
          "verify provider readiness, credentials, endpoint, and query compatibility",
          failure);
    }
  }

  @Override
  public TelemetryAssertionResult assertEventually(
      TelemetryQuery query,
      Predicate<TelemetryQueryResult> expectation,
      Duration timeout,
      Duration pollInterval) {
    Objects.requireNonNull(expectation, "expectation");
    requirePositive(timeout, "Assertion timeout");
    requirePositive(pollInterval, "Assertion poll interval");
    long started = System.nanoTime();
    long timeoutNanos = timeout.toNanos();
    int polls = 0;
    TelemetryQueryResult last;
    do {
      last = query(query);
      polls++;
      if (expectation.test(last)) {
        return new TelemetryAssertionResult(
            TelemetryAssertionResult.Status.MATCHED, polls, elapsed(started), last);
      }
      long remaining = timeoutNanos - (System.nanoTime() - started);
      if (remaining <= 0) {
        break;
      }
      try {
        Thread.sleep(Duration.ofNanos(Math.min(remaining, pollInterval.toNanos())));
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new ObservabilityAssertionException(
            "Telemetry assertion interrupted after " + polls + " polls", failure);
      }
    } while (System.nanoTime() - started < timeoutNanos);
    return new TelemetryAssertionResult(
        TelemetryAssertionResult.Status.TIMED_OUT, polls, elapsed(started), last);
  }

  @Override
  public void close() {
    state.set(ControllerState.CLOSED);
  }

  private TelemetryQueryResult sanitize(TelemetryQuery query, TelemetryQueryResult result) {
    int returned =
        Math.min(Math.min(result.records().size(), query.limit()), limits.maximumRecords());
    List<TelemetryRecord> records = new ArrayList<>(returned);
    for (TelemetryRecord record : result.records().subList(0, returned)) {
      Map<String, String> attributes = new LinkedHashMap<>();
      record.attributes().forEach((key, value) -> attributes.put(redact(key), redact(value)));
      String payload = redact(record.payload());
      if (payload.length() > limits.maximumPayloadChars()) {
        payload = payload.substring(0, limits.maximumPayloadChars()) + "…[TRUNCATED]";
      }
      records.add(new TelemetryRecord(record.timestamp(), attributes, payload));
    }
    boolean truncated =
        result.truncated()
            || result.records().size() > returned
            || result.matchedCount() > returned;
    String summary =
        query.type()
            + " matched="
            + result.matchedCount()
            + ", returned="
            + returned
            + ", truncated="
            + truncated;
    return new TelemetryQueryResult(
        query.type(),
        records,
        Math.max(result.matchedCount(), result.records().size()),
        truncated,
        summary);
  }

  private void validateBounds(TelemetryQuery query) {
    Objects.requireNonNull(query, "query");
    if (query.window().compareTo(limits.maximumWindow()) > 0) {
      throw new IllegalArgumentException(
          "Telemetry query window exceeds " + limits.maximumWindow());
    }
    if (query.limit() > limits.maximumRecords()) {
      throw new IllegalArgumentException(
          "Telemetry query limit exceeds " + limits.maximumRecords());
    }
  }

  private String redact(String value) {
    String redacted = redaction.redact(value);
    return redacted == null ? "" : redacted;
  }

  private void requireReady() {
    if (state.get() != ControllerState.READY) {
      throw new IllegalStateException("Observability controller is not READY: " + state.get());
    }
  }

  private static void requirePositive(Duration value, String label) {
    Objects.requireNonNull(value, label);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(label + " must be positive");
    }
  }

  private static Duration elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started);
  }
}
