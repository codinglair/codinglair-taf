package com.codinglair.taf.mobile.appium;

import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.service.DefaultAppleController;
import com.sun.net.httpserver.HttpServer;
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
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          Object decoded = body.isBlank() ? "" : new Json().toType(body, Map.class);
          requests.add(exchange.getRequestMethod() + " " + path + " " + decoded);
          Object value = null;
          int status = 200;
          boolean evidenceRequest =
              path.endsWith("/screenshot")
                  || path.endsWith("/source")
                  || path.endsWith("/log")
                  || path.endsWith("/log/types")
                  || path.endsWith("/start_recording_screen")
                  || path.endsWith("/stop_recording_screen");
          if (evidenceRequest
              && (evidenceOutcome.equals("unsupported")
                  || evidenceOutcome.equals("collection-failed"))) {
            status = 500;
            value =
                Map.of(
                    "error",
                    evidenceOutcome.equals("unsupported")
                        ? "unsupported operation"
                        : "unknown error",
                    "message",
                    "CANARY",
                    "stacktrace",
                    "CANARY");
          } else if (reject || (stale && path.endsWith("/click"))) {
            status = stale ? 404 : 500;
            value =
                Map.of(
                    "error",
                    stale ? "stale element reference" : "unknown error",
                    "message",
                    "CANARY provider secret",
                    "stacktrace",
                    "CANARY");
          } else if (path.equals("/session")) {
            value = Map.of("sessionId", "fixture", "capabilities", Map.of("platformName", "iOS"));
          } else if (path.endsWith("/screenshot") || path.endsWith("/stop_recording_screen")) {
            value =
                evidenceValue != null
                    ? evidenceValue
                    : evidenceOutcome.equals("unavailable") ? "" : "aW1hZ2U=";
          } else if (path.endsWith("/source")) {
            value =
                sourceValue != null
                    ? sourceValue
                    : evidenceValue != null
                        ? evidenceValue
                        : evidenceOutcome.equals("unavailable")
                            ? ""
                            : "<page password=\"CANARY\"/>";
          } else if (path.endsWith("/log/types")) {
            value = List.of("syslog", "server");
          } else if (path.endsWith("/log")) {
            value =
                evidenceOutcome.equals("unavailable")
                    ? List.of()
                    : List.of(
                        Map.of("timestamp", 1, "level", "INFO", "message", "password=CANARY"));
          } else if (path.endsWith("/contexts")) {
            contextReads++;
            value =
                missing || contextReads <= readyAfter
                    ? List.of("NATIVE_APP")
                    : List.of("NATIVE_APP", "WEBVIEW_A", "WEBVIEW_B");
          } else if (path.endsWith("/context")) {
            if (exchange.getRequestMethod().equals("POST")) {
              Map<?, ?> parameters = new Json().toType(body, Map.class);
              context = (String) parameters.get("name");
            } else value = context;
          } else if (path.endsWith("/element")) {
            value = Map.of("element-6066-11e4-a52e-4f735466cecf", "element-1");
          } else if (path.endsWith("/text")) value = "Order submitted";
          else if (path.endsWith("/rect"))
            value = Map.of("x", 0, "y", 0, "width", 400, "height", 800);
          else if (path.endsWith("/app_state")) value = 4;
          else if (path.endsWith("/terminate_app") || path.endsWith("/remove_app")) value = true;
          else if (body.contains("mobile: queryAppState")) value = 4;
          else if (body.contains("mobile: terminateApp") || body.contains("mobile: removeApp"))
            value = true;
          String payload = "{\"value\":" + new Json().toJson(value) + "}";
          byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          try (var output = exchange.getResponseBody()) {
            output.write(bytes);
          }
        });
    server.start();
  }

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
