package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.appium.configuration.AppleAutoConfiguration;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.exception.AppleControllerException;
import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.mobile.appium.service.DefaultAppleController;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openqa.selenium.json.Json;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Apple W3C protocol and scoped lifecycle")
class AppleSessionProtocolTest {
  private HttpServer server;
  private final List<String> paths = new CopyOnWriteArrayList<>();
  private final List<String> requests = new CopyOnWriteArrayList<>();
  private final AtomicInteger identifiers = new AtomicInteger();
  private volatile boolean reject;
  private volatile CountDownLatch entered;
  private volatile CountDownLatch release;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/custom/wd/hub",
        exchange -> {
          paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
          requests.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          if (entered != null && exchange.getRequestMethod().equals("POST")) {
            entered.countDown();
            try {
              if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("fixture timed out");
            } catch (InterruptedException failure) {
              Thread.currentThread().interrupt();
              throw new IOException("fixture interrupted");
            }
          }
          String payload;
          int status = 200;
          if (reject) {
            status = 500;
            payload =
                """
            {"value":{"error":"session not created","message":"CANARY sensitive provider response","stacktrace":"CANARY"}}
            """;
          } else if (exchange.getRequestMethod().equals("POST")
              && exchange.getRequestURI().getPath().endsWith("/session")) {
            payload =
                new Json()
                    .toJson(
                        Map.of(
                            "value",
                            Map.of(
                                "sessionId",
                                "fixture-" + identifiers.incrementAndGet(),
                                "capabilities",
                                Map.of("platformName", "iOS"))));
          } else if (exchange.getRequestMethod().equals("POST")) payload = "{\"value\":true}";
          else payload = "{\"value\":null}";
          byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(status, bytes.length);
          try (var output = exchange.getResponseBody()) {
            output.write(bytes);
          }
        });
    server.start();
  }

  @AfterEach
  void stop() {
    if (release != null) release.countDown();
    server.stop(0);
  }

  private AppleControllerSettings settings() {
    var settings = AppleConfigurationTest.valid();
    settings.setServerUrl(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/custom/wd/hub"));
    settings.setTerminateAppOnClose(false);
    return settings;
  }

  @ParameterizedTest
  @EnumSource(MobileExecutionMode.class)
  @DisplayName("constructs IOSDriver and cleans up exactly once through the exact endpoint path")
  void create(MobileExecutionMode mode) {
    var settings = settings();
    settings.setExecutionMode(mode);
    if (mode == MobileExecutionMode.SAFARI) {
      settings.setBundleId(null);
      settings.setTerminateAppOnClose(null);
    }
    var controller = new DefaultAppleController("fixture", settings, null);
    assertThat(paths).isEmpty();
    try {
      controller.initialize(TestContexts.context());
      assertThat(controller.state()).isEqualTo(ControllerState.READY);
      assertThat(controller.nativeDriver().getSessionId().toString()).isEqualTo("fixture-1");
      assertThat(requests.getFirst()).contains("XCUITest", "iOS");
      if (mode == MobileExecutionMode.SAFARI)
        assertThat(requests.getFirst()).contains("Safari").doesNotContain("bundleId");
    } finally {
      controller.close();
    }
    controller.close();
    assertThat(paths)
        .containsExactly("POST /custom/wd/hub/session", "DELETE /custom/wd/hub/session/fixture-1");
    assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
    assertThrows(IllegalStateException.class, controller::nativeDriver);
  }

  @Test
  @DisplayName("sanitizes rejected sessions and permits idempotent cleanup")
  void rejected() {
    reject = true;
    var controller = new DefaultAppleController("fixture", settings(), null);
    var failure =
        assertThrows(
            AppleControllerException.class, () -> controller.initialize(TestContexts.context()));
    assertThat(failure).hasMessageNotContaining("CANARY").hasCause(null);
    assertThat(controller.state()).isEqualTo(ControllerState.FAILED);
    controller.close();
    controller.close();
    assertThat(paths).allMatch(path -> path.startsWith("POST "));
  }

  @Test
  @DisplayName("rejects invalid configuration without any protocol connection")
  void invalid() {
    var settings = settings();
    settings.setAutomationName("UiAutomator2");
    var controller = new DefaultAppleController("fixture", settings, null);
    assertThrows(
        AppleControllerException.class, () -> controller.initialize(TestContexts.context()));
    controller.close();
    assertThat(paths).isEmpty();
  }

  @Test
  @DisplayName("serializes concurrent initialize and close without leaking the acquired session")
  void closeRace() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(10),
        () -> {
          entered = new CountDownLatch(1);
          release = new CountDownLatch(1);
          var controller = new DefaultAppleController("fixture", settings(), null);
          try (var executor = Executors.newFixedThreadPool(2)) {
            var init = executor.submit(() -> controller.initialize(TestContexts.context()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var close = executor.submit(controller::close);
            release.countDown();
            init.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);
          } finally {
            controller.close();
          }
          assertThat(controller.state()).isEqualTo(ControllerState.CLOSED);
          assertThat(paths)
              .containsExactly(
                  "POST /custom/wd/hub/session", "DELETE /custom/wd/hub/session/fixture-1");
        });
  }

  @Test
  @DisplayName("initializes through the named TestSession registry and releases on session close")
  void verticalSession() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(AppleAutoConfiguration.class, TafRuntimeAutoConfiguration.class))
        .withPropertyValues(
            "taf.mobile.apple.enabled=true",
            "taf.mobile.apple.platform=ios",
            "taf.mobile.apple.device-kind=simulator",
            "taf.mobile.apple.device-name=fixture",
            "taf.mobile.apple.server-url=" + settings().getServerUrl(),
            "taf.mobile.apple.bundle-id=com.example.fixture")
        .run(
            c -> {
              try (var session = c.getBean(TestSessionFactory.class).create()) {
                assertThat(paths).isEmpty();
                var controller =
                    session.getControllerRegistry().get(AppleController.class, "default");
                assertThat(controller.state()).isEqualTo(ControllerState.READY);
                assertThat(session.getControllerRegistry().get(AppleController.class, "default"))
                    .isSameAs(controller);
              }
              assertThat(paths)
                  .containsExactly(
                      "POST /custom/wd/hub/session",
                      "POST /custom/wd/hub/session/fixture-1/execute/sync",
                      "DELETE /custom/wd/hub/session/fixture-1");
            });
  }

  @Test
  @DisplayName("preserves Android UiAutomator2 protocol and public settings")
  void androidRegression() throws Exception {
    var android = new AndroidControllerSettings();
    android.setServerUrl(settings().getServerUrl());
    android.setDeviceName("android-fixture");
    android.setAppPackage("com.example.android");
    var driver = AppiumAndroidSessionFactory.standard().create(android);
    try {
      assertThat(requests.getFirst()).contains("UIAutomator2", "com.example.android");
    } finally {
      driver.quit();
    }
    assertThat(paths)
        .containsExactly("POST /custom/wd/hub/session", "DELETE /custom/wd/hub/session/fixture-1");
  }
}
