package com.codinglair.taf.mobile.appium;

import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.service.DefaultAppleController;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.json.Json;

abstract class AppleProtocolFixture {
  HttpServer server;
  final List<String> requests = new CopyOnWriteArrayList<>();
  String context = "NATIVE_APP";
  boolean missing;
  boolean reject;
  boolean stale;
  int contextReads;
  int readyAfter;
  String evidenceOutcome = "available";
  String evidenceValue;
  String sourceValue;
  DefaultAppleController controller;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handleRequest);
    server.start();
  }

  private void handleRequest(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Object decoded = body.isBlank() ? "" : new Json().toType(body, Map.class);
    requests.add(exchange.getRequestMethod() + " " + path + " " + decoded);
    writeResponse(exchange, responseFor(exchange, path, body));
  }

  private FixtureResponse responseFor(HttpExchange exchange, String path, String body) {
    if (isEvidenceRequest(path)
        && (evidenceOutcome.equals("unsupported") || evidenceOutcome.equals("collection-failed"))) {
      return new FixtureResponse(500, evidenceFailure());
    }
    if (reject || (stale && path.endsWith("/click"))) {
      return new FixtureResponse(stale ? 404 : 500, requestFailure());
    }
    return new FixtureResponse(200, successfulResponse(exchange, path, body));
  }

  private Object successfulResponse(HttpExchange exchange, String path, String body) {
    if (path.equals("/session")) {
      return sessionResponse();
    }
    if (isScreenshotOrRecording(path)) {
      return evidenceResponse();
    }
    if (path.endsWith("/source")) {
      return sourceResponse();
    }
    if (path.endsWith("/log/types")) {
      return List.of("syslog", "server");
    }
    if (path.endsWith("/log")) {
      return logResponse();
    }
    if (path.endsWith("/contexts")) {
      return availableContexts();
    }
    if (path.endsWith("/context")) {
      return contextResponse(exchange, body);
    }
    if (path.endsWith("/element")) {
      return Map.of("element-6066-11e4-a52e-4f735466cecf", "element-1");
    }
    if (path.endsWith("/text")) {
      return "Order submitted";
    }
    if (path.endsWith("/rect")) {
      return Map.of("x", 0, "y", 0, "width", 400, "height", 800);
    }
    if (isQueryAppState(path, body)) {
      return 4;
    }
    if (isSuccessfulAppCommand(path, body)) {
      return true;
    }
    return null;
  }

  private Map<String, Object> sessionResponse() {
    return Map.of("sessionId", "fixture", "capabilities", Map.of("platformName", "iOS"));
  }

  private boolean isScreenshotOrRecording(String path) {
    return path.endsWith("/screenshot") || path.endsWith("/stop_recording_screen");
  }

  private Object evidenceResponse() {
    if (evidenceValue != null) {
      return evidenceValue;
    }
    return evidenceOutcome.equals("unavailable") ? "" : "aW1hZ2U=";
  }

  private Object sourceResponse() {
    if (sourceValue != null) {
      return sourceValue;
    }
    if (evidenceValue != null) {
      return evidenceValue;
    }
    return evidenceOutcome.equals("unavailable") ? "" : "<page password=\"CANARY\"/>";
  }

  private List<?> logResponse() {
    if (evidenceOutcome.equals("unavailable")) {
      return List.of();
    }
    return List.of(Map.of("timestamp", 1, "level", "INFO", "message", "password=CANARY"));
  }

  private boolean isQueryAppState(String path, String body) {
    return path.endsWith("/app_state") || body.contains("mobile: queryAppState");
  }

  private boolean isSuccessfulAppCommand(String path, String body) {
    return path.endsWith("/terminate_app")
        || path.endsWith("/remove_app")
        || body.contains("mobile: terminateApp")
        || body.contains("mobile: removeApp");
  }

  private List<String> availableContexts() {
    contextReads++;
    return missing || contextReads <= readyAfter
        ? List.of("NATIVE_APP")
        : List.of("NATIVE_APP", "WEBVIEW_A", "WEBVIEW_B");
  }

  private Object contextResponse(HttpExchange exchange, String body) {
    if (exchange.getRequestMethod().equals("POST")) {
      Map<?, ?> parameters = new Json().toType(body, Map.class);
      context = (String) parameters.get("name");
      return null;
    }
    return context;
  }

  private boolean isEvidenceRequest(String path) {
    return path.endsWith("/screenshot")
        || path.endsWith("/source")
        || path.endsWith("/log")
        || path.endsWith("/log/types")
        || path.endsWith("/start_recording_screen")
        || path.endsWith("/stop_recording_screen");
  }

  private Map<String, String> evidenceFailure() {
    return Map.of(
        "error",
        evidenceOutcome.equals("unsupported") ? "unsupported operation" : "unknown error",
        "message",
        "CANARY",
        "stacktrace",
        "CANARY");
  }

  private Map<String, String> requestFailure() {
    return Map.of(
        "error",
        stale ? "stale element reference" : "unknown error",
        "message",
        "CANARY provider secret",
        "stacktrace",
        "CANARY");
  }

  private void writeResponse(HttpExchange exchange, FixtureResponse response) throws IOException {
    String payload = "{\"value\":" + new Json().toJson(response.value()) + "}";
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(response.status(), bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private record FixtureResponse(int status, Object value) {}

  AppleControllerSettings settings() {
    var settings = AppleConfigurationTest.valid();
    settings.setServerUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    settings.setTerminateAppOnClose(false);
    settings.setContextTimeout(Duration.ofMillis(70));
    return settings;
  }

  void initialize(AppleControllerSettings settings) {
    controller = new DefaultAppleController("operations", settings, null);
    controller.initialize(TestContexts.context());
  }

  @AfterEach
  void stop() {
    reject = false;
    if (controller != null) controller.close();
    server.stop(0);
  }
}
