package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.appium.configuration.AppleAutoConfiguration;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.exception.AppleControllerException;
import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.mobile.appium.service.AppleResourceReservations;
import com.codinglair.taf.mobile.appium.service.DefaultAppleController;
import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.autoconfigure.TafRuntimeAutoConfiguration;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.lifecycle.InvocationDescriptor;
import com.codinglair.taf.runtime.core.lifecycle.InvocationOutcome;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionLifecycle;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
  private volatile boolean rejectDelete;
  private ExecutorService serverExecutor;
  private volatile BooleanSupplier evidenceCheck;
  private final AtomicBoolean evidenceBeforeDelete = new AtomicBoolean();

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(serverExecutor);
    server.createContext(
        "/custom/wd/hub",
        exchange -> {
          paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
          if (exchange.getRequestMethod().equals("DELETE") && evidenceCheck != null)
            evidenceBeforeDelete.set(evidenceCheck.getAsBoolean());
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
          if (reject || (rejectDelete && exchange.getRequestMethod().equals("DELETE"))) {
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
    serverExecutor.shutdownNow();
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
    var settings = settings();
    settings.setDeviceId("rejected-target");
    var coordinator = new AppleResourceReservations();
    var controller = new DefaultAppleController("fixture", settings, null, coordinator);
    var failure =
        assertThrows(
            AppleControllerException.class, () -> controller.initialize(TestContexts.context()));
    assertThat(failure).hasMessageNotContaining("CANARY").hasCause(null);
    assertThat(controller.state()).isEqualTo(ControllerState.FAILED);
    controller.close();
    controller.close();
    assertThat(paths).allMatch(path -> path.startsWith("POST "));
    reject = false;
    var next = new DefaultAppleController("next", settings, null, coordinator);
    try {
      next.initialize(TestContexts.context());
    } finally {
      next.close();
    }
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

  @Test
  @DisplayName("cancellation before initialization acquires nothing and preserves interruption")
  void cancelled() {
    var controller = new DefaultAppleController("cancelled", settings(), null);
    Thread.currentThread().interrupt();
    try {
      assertThrows(
          AppleControllerException.class, () -> controller.initialize(TestContexts.context()));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(paths).isEmpty();
    } finally {
      Thread.interrupted();
      controller.close();
    }
  }

  @Test
  @DisplayName("concurrent controllers keep separate driver identities and reject shared targets")
  void isolated() throws Exception {
    var coordinator = new AppleResourceReservations();
    var firstSettings = settings();
    firstSettings.setDeviceId("target-a");
    var secondSettings = settings();
    secondSettings.setDeviceId("target-b");
    var first = new DefaultAppleController("first", firstSettings, null, coordinator);
    var second = new DefaultAppleController("second", secondSettings, null, coordinator);
    var collision = new DefaultAppleController("collision", firstSettings, null, coordinator);
    var firstSession = TestSession.create();
    var secondSession = TestSession.create();
    firstSession.getControllerRegistry().register(AppleController.class, "first", first);
    secondSession.getControllerRegistry().register(AppleController.class, "second", second);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a =
          executor.submit(
              () -> firstSession.getControllerRegistry().get(AppleController.class, "first"));
      var b =
          executor.submit(
              () -> secondSession.getControllerRegistry().get(AppleController.class, "second"));
      a.get(5, TimeUnit.SECONDS);
      b.get(5, TimeUnit.SECONDS);
      assertThat(first.nativeDriver()).isNotSameAs(second.nativeDriver());
      assertThat(first.nativeDriver().getSessionId())
          .isNotEqualTo(second.nativeDriver().getSessionId());
      assertThrows(
          AppleControllerException.class, () -> collision.initialize(TestContexts.context()));
      assertThat(identifiers.get()).isEqualTo(2);
      collision.close();
      var foreignContext = TestContexts.context();
      assertThrows(IllegalStateException.class, () -> first.initialize(foreignContext));
      firstSession.close();
      assertThat(firstSession.getArtifactCollector().getArtifacts()).hasSize(1);
      assertThat(secondSession.getArtifactCollector().getArtifacts()).isEmpty();
      assertThat(foreignContext.artifacts().getArtifacts()).isEmpty();
      assertThat(second.state()).isEqualTo(ControllerState.READY);
    } finally {
      firstSession.close();
      secondSession.close();
    }
    var reused = new DefaultAppleController("reused", firstSettings, null, coordinator);
    try {
      reused.initialize(TestContexts.context());
    } finally {
      reused.close();
    }
    assertThat(paths.stream().filter(p -> p.startsWith("DELETE"))).hasSize(3);
  }

  @Test
  @DisplayName(
      "ambiguous creation timeout reports uncertainty and quarantines observable resources")
  void uncertainTimeout() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(8),
        () -> {
          entered = new CountDownLatch(1);
          release = new CountDownLatch(1);
          var settings = settings();
          settings.setReadinessTimeout(Duration.ofMillis(100));
          settings.setDeviceId("uncertain-target");
          var coordinator = new AppleResourceReservations();
          var controller = new DefaultAppleController("timeout", settings, null, coordinator);
          var failure =
              assertThrows(
                  AppleControllerException.class,
                  () -> controller.initialize(TestContexts.context()));
          assertThat(failure).hasMessageContaining("uncertain").hasCause(null);
          assertThat(entered.getCount()).isZero();
          controller.close();
          assertThrows(IllegalStateException.class, () -> coordinator.acquire(settings));
          assertThat(paths).allMatch(p -> p.startsWith("POST"));
          release.countDown();
        });
  }

  @Test
  @DisplayName(
      "cancellation during session creation preserves interruption and uncertain ownership")
  void cancelledCreation() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(8),
        () -> {
          entered = new CountDownLatch(1);
          release = new CountDownLatch(1);
          var finished = new CountDownLatch(1);
          var interrupted = new AtomicBoolean();
          var settings = settings();
          settings.setDeviceId("cancelled-target");
          var coordinator = new AppleResourceReservations();
          var controller = new DefaultAppleController("cancel", settings, null, coordinator);
          var worker =
              Thread.ofVirtual()
                  .start(
                      () -> {
                        try {
                          controller.initialize(TestContexts.context());
                        } catch (AppleControllerException _) {
                          interrupted.set(Thread.currentThread().isInterrupted());
                        } finally {
                          finished.countDown();
                        }
                      });
          try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.interrupt();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted).isTrue();
            assertThat(controller.state()).isEqualTo(ControllerState.FAILED);
            assertThat(controller.health().diagnostics()).containsKey("ownership");
            controller.close();
            assertThrows(IllegalStateException.class, () -> coordinator.acquire(settings));
          } finally {
            release.countDown();
            worker.interrupt();
            controller.close();
          }
        });
  }

  @Test
  @DisplayName("quit failure stays sanitized, runs once and keeps uncertain allocation reserved")
  void failedCleanup() {
    var settings = settings();
    settings.setDeviceId("cleanup-target");
    var coordinator = new AppleResourceReservations();
    var controller = new DefaultAppleController("cleanup", settings, null, coordinator);
    controller.initialize(TestContexts.context());
    rejectDelete = true;
    var failure = assertThrows(AppleControllerException.class, controller::close);
    assertThat(failure).hasMessageNotContaining("CANARY").hasCause(null);
    controller.close();
    assertThat(paths.stream().filter(p -> p.startsWith("DELETE"))).hasSize(1);
    assertThrows(IllegalStateException.class, () -> coordinator.acquire(settings));
  }

  @Test
  @DisplayName(
      "provider allocations supply session-local target and WDA resources without provisioning")
  void allocated() {
    var settings = settings();
    settings.setAllocationResource("allocation");
    var context = TestContexts.context();
    var allocatedContext =
        new ControllerContext(
            context.sessionId(),
            name ->
                new EnvironmentAccess.Resource(
                    "owned",
                    "apple-session-allocation",
                    Map.of(
                        "exclusive",
                        "true",
                        "device-id",
                        "provider-target",
                        "wda-port",
                        "8101",
                        "mjpeg-port",
                        "9101",
                        "derived-data-path",
                        "/owned/session-a")),
            context.artifacts());
    var controller = new DefaultAppleController("allocated", settings, null);
    try {
      controller.initialize(allocatedContext);
    } finally {
      controller.close();
    }
    assertThat(requests.getFirst()).contains("provider-target", "8101", "9101");
    Map<?, ?> request = new Json().toType(requests.getFirst(), Map.class);
    Map<?, ?> capabilities = (Map<?, ?>) request.get("capabilities");
    Map<?, ?> match = (Map<?, ?>) ((List<?>) capabilities.get("firstMatch")).getFirst();
    assertThat(match.get("appium:derivedDataPath")).isEqualTo("/owned/session-a");
    assertThat(settings.getDeviceId()).isNull();
    assertThat(settings.getWda().getLocalPort()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName("identifiable uncertain sessions receive only an owned DELETE and retain quarantine")
  void identifiableTimeout(boolean cleanupFails) {
    assertTimeoutPreemptively(
        Duration.ofSeconds(8),
        () -> {
          entered = new CountDownLatch(1);
          release = new CountDownLatch(1);
          rejectDelete = cleanupFails;
          var settings = settings();
          settings.setReadinessTimeout(Duration.ofMillis(100));
          settings.setCleanupTimeout(Duration.ofSeconds(1));
          settings.setAllocationResource("allocation");
          var context = TestContexts.context();
          var owned =
              new ControllerContext(
                  context.sessionId(),
                  name ->
                      new EnvironmentAccess.Resource(
                          "owned",
                          "apple-session-allocation",
                          Map.of(
                              "exclusive",
                              "true",
                              "device-id",
                              "provider-target",
                              "owned-session-id",
                              "owned-session")),
                  context.artifacts());
          var controller = new DefaultAppleController("timeout", settings, null);
          var failure =
              assertThrows(AppleControllerException.class, () -> controller.initialize(owned));
          assertThat(failure).hasMessageContaining("uncertain").hasCause(null);
          assertThat(failure.getSuppressed()).hasSize(cleanupFails ? 1 : 0);
          controller.close();
          assertThat(paths)
              .containsExactly(
                  "POST /custom/wd/hub/session", "DELETE /custom/wd/hub/session/owned-session");
          release.countDown();
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "type",
        "exclusive",
        "device-id",
        "wda-port",
        "derived-data-path",
        "owned-session-id"
      })
  @DisplayName("invalid allocation metadata fails sanitized before a connection or reservation")
  void invalidAllocation(String dimension) {
    var settings = settings();
    settings.setAllocationResource("allocation");
    var properties = new LinkedHashMap<>(Map.of("exclusive", "true", "device-id", "target"));
    properties.put(dimension, dimension.equals("device-id") ? "" : "CANARY/../invalid");
    var context = TestContexts.context();
    var allocated =
        new ControllerContext(
            context.sessionId(),
            name ->
                new EnvironmentAccess.Resource(
                    "owned",
                    dimension.equals("type") ? "CANARY" : "apple-session-allocation",
                    properties),
            context.artifacts());
    var controller = new DefaultAppleController("invalid", settings, null);
    var failure =
        assertThrows(AppleControllerException.class, () -> controller.initialize(allocated));
    assertThat(failure).hasMessageNotContaining("CANARY").hasCause(null);
    controller.close();
    assertThat(paths).isEmpty();
  }

  @Test
  @DisplayName(
      "runner lifecycle preserves the primary failure and gathers evidence before teardown")
  void primaryFailure() {
    var controller = new DefaultAppleController("lifecycle", settings(), null);
    var lifecycle =
        new TestSessionLifecycle(
            () -> {
              var session = TestSession.create();
              session
                  .getControllerRegistry()
                  .register(AppleController.class, "lifecycle", controller);
              return session;
            });
    var descriptor = new InvocationDescriptor("invocation", "fixture", "test");
    var session = lifecycle.open(descriptor);
    session.getControllerRegistry().get(AppleController.class, "lifecycle");
    evidenceCheck = () -> !session.getArtifactCollector().getArtifacts().isEmpty();
    var primary = new IllegalStateException("primary test failure");
    rejectDelete = true;
    lifecycle.close(descriptor, InvocationOutcome.failed(primary));
    assertThat(primary).hasMessage("primary test failure");
    assertThat(primary.getSuppressed()).hasSize(1);
    assertThat(evidenceBeforeDelete).isTrue();
    assertThat(session.getArtifactCollector().getArtifacts()).hasSize(1);
    assertThat(lifecycle.activeOwnerships()).isEmpty();
    session.close();
    assertThat(paths.stream().filter(p -> p.startsWith("DELETE"))).hasSize(1);
  }
}
