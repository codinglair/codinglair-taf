package com.codinglair.taf.api.rest;

import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class DefaultRestController implements RestController {
  private final ControllerIdentity identity;
  private final RestControllerSettings settings;
  private final RestContractValidator validator;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private ControllerContext context;

  DefaultRestController(
      String name, RestControllerSettings settings, RestContractValidator validator) {
    identity = new ControllerIdentity(RestController.class, name);
    this.settings = Objects.requireNonNull(settings);
    this.validator = Objects.requireNonNull(validator);
  }

  public ControllerIdentity identity() {
    return identity;
  }

  public ControllerState state() {
    return state.get();
  }

  public void initialize(ControllerContext value) {
    if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING))
      throw new IllegalStateException("Controller cannot initialize from " + state.get());
    try {
      settings.validate("taf.api.rest.controllers." + identity.name());
      context = Objects.requireNonNull(value);
      state.set(ControllerState.READY);
    } catch (RuntimeException failure) {
      state.set(ControllerState.FAILED);
      throw new RestControllerException(
          "initialize", "correct the named REST controller configuration", failure);
    }
  }

  public HealthResult health() {
    return state.get() == ControllerState.READY
        ? new HealthResult(
            HealthResult.Status.HEALTHY,
            "REST controller is ready",
            Map.of("baseUrl", settings.getBaseUrl().toString()))
        : HealthResult.unknown("REST controller is not ready: " + state.get());
  }

  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    return Stream.empty();
  }

  public void close() {
    state.set(ControllerState.CLOSED);
  }

  @Override
  @ControllerAction("Execute REST request")
  public RestResponse execute(RestRequest request) {
    ensureReady();
    Objects.requireNonNull(request);
    Instant startedAt = Instant.now();
    long startedNanos = System.nanoTime();
    RestResponse response = null;
    Throwable executionFailure = null;
    try {
      RequestSpecification specification = nativeSpecification();
      specification
          .headers(request.headers())
          .cookies(request.cookies())
          .pathParams(request.pathParameters())
          .queryParams(request.queryParameters());
      if (!request.formParameters().isEmpty()) specification.formParams(request.formParameters());
      if (request.contentType() != null) specification.contentType(request.contentType());
      if (request.body().length > 0) specification.body(request.body());
      request
          .multiparts()
          .forEach(
              part ->
                  specification.multiPart(
                      part.controlName(), part.fileName(), part.content(), part.contentType()));
      request.authentication().apply(specification);
      Response nativeResponse = specification.request(request.method(), request.path());
      response = new RestResponse(nativeResponse);
      validator.validate(request, response);
      return response;
    } catch (AssertionError failure) {
      executionFailure = failure;
      throw failure;
    } catch (Throwable failure) {
      executionFailure = failure;
      if (failure instanceof RuntimeException runtimeFailure) {
        throw new RestControllerException(
            "execute " + request.method(),
            "verify endpoint, request data, authentication, and network availability",
            runtimeFailure);
      }
      throwUnchecked(failure);
      return null;
    } finally {
      capture(
          request,
          response,
          executionFailure,
          startedAt,
          Duration.ofNanos(System.nanoTime() - startedNanos));
    }
  }

  @Override
  @ControllerAction("Poll REST request")
  public RestResponse await(
      RestRequest request, Predicate<RestResponse> condition, Duration timeout, Duration interval) {
    Objects.requireNonNull(condition);
    requirePositive(timeout, "timeout");
    requirePositive(interval, "interval");
    long deadline = System.nanoTime() + timeout.toNanos();
    RestResponse latest;
    do {
      latest = execute(request);
      if (condition.test(latest)) return latest;
      waitForNextAttempt(interval, deadline);
    } while (System.nanoTime() < deadline);
    throw new RestControllerException(
        "poll",
        "increase the timeout or correct the eventual condition",
        new AssertionError("Condition was not satisfied"));
  }

  public RequestSpecification nativeSpecification() {
    ensureReady();
    return RestAssured.given()
        .baseUri(settings.getBaseUrl().toString())
        .config(
            RestAssured.config()
                .httpClient(
                    RestAssured.config()
                        .getHttpClientConfig()
                        .setParam("http.connection.timeout", (int) settings.getTimeout().toMillis())
                        .setParam("http.socket.timeout", (int) settings.getTimeout().toMillis())));
  }

  private void capture(
      RestRequest request,
      RestResponse response,
      Throwable failure,
      Instant startedAt,
      Duration duration) {
    String operationId = context.sessionId() + "-rest-" + context.artifacts().nextSequenceNumber();
    String stepName = request.method() + " " + safeRoute(request.path());
    context
        .artifacts()
        .addArtifact(
            operationId + "-request.txt",
            "http-request",
            requestEvidence(request, operationId, startedAt, duration),
            "text/plain",
            stepName);
    if (response != null) {
      context
          .artifacts()
          .addArtifact(
              operationId + "-response.txt",
              "http-response",
              responseEvidence(response, operationId, duration),
              "text/plain",
              stepName);
    }
    if (failure != null) {
      context
          .artifacts()
          .addArtifact(
              operationId + "-failure.txt",
              "http-failure",
              failureEvidence(failure, operationId, duration, response == null),
              "text/plain",
              stepName);
    }
  }

  private static void waitForNextAttempt(Duration interval, long deadline) {
    long remainingMillis = Duration.ofNanos(Math.max(0, deadline - System.nanoTime())).toMillis();
    try {
      Thread.sleep(Math.min(interval.toMillis(), Math.max(1, remainingMillis)));
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new RestControllerException(
          "poll", "preserve cancellation and retry only when appropriate", failure);
    }
  }

  private String requestEvidence(
      RestRequest request, String operationId, Instant startedAt, Duration duration) {
    return "operationId="
        + operationId
        + "\nsessionId="
        + context.sessionId()
        + "\nstartedAt="
        + startedAt
        + "\ndurationMs="
        + duration.toMillis()
        + "\nmethod="
        + request.method()
        + "\nroute="
        + safeRoute(request.path())
        + "\npathParameters="
        + RestEvidenceSanitizer.namedValues(request.pathParameters())
        + "\nqueryParameters="
        + RestEvidenceSanitizer.namedValues(request.queryParameters())
        + "\nheaders="
        + RestEvidenceSanitizer.namedValues(request.headers())
        + "\ncookies="
        + (request.cookies().isEmpty() ? "{}" : "[REDACTED]")
        + "\ncontentType="
        + Objects.toString(request.contentType(), "unknown")
        + "\nbody="
        + RestEvidenceSanitizer.body(
            request.body(), request.contentType(), settings.getMaxEvidenceBytes());
  }

  private String responseEvidence(RestResponse response, String operationId, Duration duration) {
    Map<String, String> headers =
        response.nativeResponse().headers().asList().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    io.restassured.http.Header::getName,
                    io.restassured.http.Header::getValue,
                    (first, second) -> second,
                    LinkedHashMap::new));
    return "operationId="
        + operationId
        + "\ndurationMs="
        + duration.toMillis()
        + "\nstatus="
        + response.statusCode()
        + "\nheaders="
        + RestEvidenceSanitizer.namedValues(headers)
        + "\ncontentType="
        + Objects.toString(response.nativeResponse().contentType(), "unknown")
        + "\nbody="
        + RestEvidenceSanitizer.body(
            response.body(),
            response.nativeResponse().contentType(),
            settings.getMaxEvidenceBytes());
  }

  private static String failureEvidence(
      Throwable failure, String operationId, Duration duration, boolean beforeResponse) {
    String phase = beforeResponse ? "connection-or-before-response" : "response-validation";
    return "operationId="
        + operationId
        + "\ndurationMs="
        + duration.toMillis()
        + "\nphase="
        + phase
        + "\nexception="
        + failure.getClass().getName()
        + "\nmessage="
        + RestEvidenceSanitizer.sanitizeText(
            Objects.requireNonNullElse(failure.getMessage(), "(no message)"));
  }

  private static String safeRoute(String path) {
    int query = path.indexOf('?');
    String route = query < 0 ? path : path.substring(0, query);
    return RestEvidenceSanitizer.sanitizeText(route.replaceAll("(?i)https?://[^/]+", ""));
  }

  private static void requirePositive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative())
      throw new IllegalArgumentException(name + " must be positive");
  }

  private void ensureReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("REST controller is not READY: " + state.get());
  }

  private static void throwUnchecked(Throwable failure) {
    DefaultRestController.<RuntimeException>throwAny(failure);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }
}
