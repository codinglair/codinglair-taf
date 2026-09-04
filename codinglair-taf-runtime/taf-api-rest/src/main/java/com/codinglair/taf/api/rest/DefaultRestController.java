package com.codinglair.taf.api.rest;

import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class DefaultRestController implements RestController {
  private static final Set<String> SENSITIVE =
      Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");
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
      RestResponse response = new RestResponse(nativeResponse);
      validator.validate(request, response);
      capture(request, response);
      return response;
    } catch (AssertionError failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new RestControllerException(
          "execute " + request.method(),
          "verify endpoint, request data, authentication, and network availability",
          failure);
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
      try {
        Thread.sleep(
            Math.min(
                interval.toMillis(),
                Math.max(
                    1, Duration.ofNanos(Math.max(0, deadline - System.nanoTime())).toMillis())));
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new RestControllerException(
            "poll", "preserve cancellation and retry only when appropriate", failure);
      }
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

  private void capture(RestRequest request, RestResponse response) {
    String requestEvidence =
        request.method()
            + " "
            + request.path()
            + "\nheaders="
            + sanitize(request.headers())
            + "\ncookies="
            + (request.cookies().isEmpty() ? "{}" : "[REDACTED]")
            + "\nbody="
            + display(request.body(), request.contentType());
    String responseEvidence =
        "status="
            + response.statusCode()
            + "\nheaders="
            + sanitize(
                response.nativeResponse().headers().asList().stream()
                    .collect(
                        java.util.stream.Collectors.toMap(
                            io.restassured.http.Header::getName,
                            io.restassured.http.Header::getValue,
                            (first, second) -> second,
                            LinkedHashMap::new)))
            + "\nbody="
            + display(response.body(), response.nativeResponse().contentType());
    context
        .artifacts()
        .addArtifact(
            "rest-request-" + context.artifacts().nextSequenceNumber() + ".txt",
            "http-request",
            requestEvidence,
            "text/plain",
            null);
    context
        .artifacts()
        .addArtifact(
            "rest-response-" + context.artifacts().nextSequenceNumber() + ".txt",
            "http-response",
            responseEvidence,
            "text/plain",
            null);
  }

  private static Map<String, Object> sanitize(Map<String, ?> source) {
    Map<String, Object> safe = new LinkedHashMap<>();
    source.forEach(
        (key, value) ->
            safe.put(key, SENSITIVE.contains(key.toLowerCase(Locale.ROOT)) ? "[REDACTED]" : value));
    return safe;
  }

  private static String display(byte[] bytes, String contentType) {
    if (bytes.length == 0) return "";
    if (contentType == null
        || !(contentType.startsWith("text/")
            || contentType.contains("json")
            || contentType.contains("xml")
            || contentType.contains("form"))) return "[BINARY " + bytes.length + " bytes]";
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static void requirePositive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative())
      throw new IllegalArgumentException(name + " must be positive");
  }

  private void ensureReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("REST controller is not READY: " + state.get());
  }
}
