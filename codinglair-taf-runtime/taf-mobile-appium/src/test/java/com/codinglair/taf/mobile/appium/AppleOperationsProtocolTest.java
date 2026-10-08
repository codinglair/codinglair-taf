package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.mobile.ApplicationMode;
import com.codinglair.taf.mobile.MobileDeviceKind;
import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.MobileOrientation;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings.AppReference;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings.LifecyclePolicy;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings.ReferenceKind;
import com.codinglair.taf.mobile.appium.exception.AppleControllerException;
import com.codinglair.taf.mobile.appium.platform.AppleLocator;
import com.codinglair.taf.mobile.appium.platform.AppleLocator.Kind;
import com.codinglair.taf.mobile.appium.service.AppleController.AndroidOperation;
import com.codinglair.taf.mobile.appium.service.DefaultAppleController;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.StaleElementReferenceException;

@DisplayName("Apple operations over the real Java client W3C transport")
class AppleOperationsProtocolTest extends AppleProtocolFixture {
  @Test
  @DisplayName("polls for late WebViews without choosing another available view")
  void lateWebView() {
    var settings = settings();
    settings.setExecutionMode(MobileExecutionMode.HYBRID);
    settings.setContextTimeout(Duration.ofSeconds(1));
    initialize(settings);
    readyAfter = 2;
    controller.selectWebView("WEBVIEW_B");
    assertThat(contextReads).isEqualTo(3);
    assertThat(context).isEqualTo("WEBVIEW_B");
  }

  @Test
  @DisplayName("rejects foreign session handles and closes the second session independently")
  void isolation() throws Exception {
    initialize(settings());
    var first = controller.find(new AppleLocator(Kind.ACCESSIBILITY, "submit"));
    var second = new DefaultAppleController("second", settings(), null);
    second.initialize(TestContexts.context());
    try {
      assertThrows(StaleElementReferenceException.class, () -> second.tap(first));
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var close = executor.submit(second::close);
        close.get(2, TimeUnit.SECONDS);
      }
      assertThrows(
          IllegalStateException.class,
          () -> second.find(new AppleLocator(Kind.ACCESSIBILITY, "submit")));
      controller.tap(first);
    } finally {
      second.close();
    }
  }

  @Test
  @DisplayName("validates context locators, coordinates, identity and URI without side effects")
  void invalidInputs() {
    initialize(settings());
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.find(new AppleLocator(Kind.CSS, "button")));
    requests.clear();
    assertThrows(
        IllegalArgumentException.class, () -> controller.swipe(-1, 0, 0, 0, Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.openDeepLink("https://user:CANARY@example.test"));
    assertThrows(UnsupportedOperationException.class, controller::install);
    assertThat(requests).isEmpty();
  }

  @Test
  @DisplayName(
      "failed context switch invalidates uncertain handles and permits explicit native recovery")
  void failedSwitch() {
    var settings = settings();
    settings.setExecutionMode(MobileExecutionMode.HYBRID);
    initialize(settings);
    var handle = controller.find(new AppleLocator(Kind.ACCESSIBILITY, "submit"));
    // Invalidate even when a context-change request has an uncertain remote result.
    reject = true;
    assertThrows(AppleControllerException.class, controller::returnToNative);
    reject = false;
    assertThrows(StaleElementReferenceException.class, () -> controller.tap(handle));
    controller.returnToNative();
    controller.tap(controller.find(new AppleLocator(Kind.ACCESSIBILITY, "submit")));
  }

  @Test
  @DisplayName(
      "dispatches lifecycle, permissions, dialogs, gestures and deep links without Android commands")
  void operations() {
    initialize(settings());
    controller.launch();
    controller.activate();
    assertThat(controller.queryAppState().name()).isEqualTo("RUNNING_IN_FOREGROUND");
    controller.terminate();
    controller.background(Duration.ofMillis(1500));
    controller.openDeepLink("fixture://order");
    controller.grantPermission("contacts");
    controller.revokePermission("contacts");
    controller.acceptDialog();
    controller.dismissDialog();
    controller.setOrientation(MobileOrientation.LANDSCAPE);
    var element = controller.find(new AppleLocator(Kind.ACCESSIBILITY, "submit-order"));
    assertThat(controller.text(element)).isEqualTo("Order submitted");
    controller.type(element, "fixture");
    controller.tap(element);
    controller.longPress(element, Duration.ofMillis(500));
    controller.swipe(20, 30, 40, 80, Duration.ofMillis(600));
    controller.openNotifications();
    assertThat(String.join("\n", requests))
        .contains(
            "mobile: launchApp",
            "mobile: activateApp",
            "mobile: terminateApp",
            "mobile: backgroundApp",
            "1.5",
            "mobile: deepLink",
            "bundleId",
            "mobile: setPermission",
            "contacts",
            "yes",
            "no",
            "/alert/accept",
            "/alert/dismiss",
            "LANDSCAPE",
            "pointerMove",
            "pointerDown",
            "pause")
        .doesNotContain("appPackage", "changePermissions", "dragGesture", "open_notifications");
  }

  @ParameterizedTest
  @EnumSource(LifecyclePolicy.class)
  @DisplayName("executes only the declared reset policy and cleans up owned installs")
  void reset(LifecyclePolicy policy) {
    var settings = settings();
    settings.setApplicationMode(ApplicationMode.PACKAGED);
    settings.setAppReference(
        new AppReference(
            ReferenceKind.SERVER_PATH, "/apps/fixture.app", MobileDeviceKind.SIMULATOR));
    settings.setLifecyclePolicy(policy);
    settings.setUninstallPackagedAppOnClose(true);
    initialize(settings);
    requests.clear();
    controller.reset();
    String actions = String.join("\n", requests);
    switch (policy) {
      case REUSE -> assertThat(requests).isEmpty();
      case RELAUNCH ->
          assertThat(actions)
              .contains("mobile: terminateApp", "mobile: activateApp")
              .doesNotContain("mobile: removeApp", "mobile: installApp");
      case REINSTALL ->
          assertThat(actions)
              .contains(
                  "mobile: removeApp",
                  "mobile: installApp",
                  "/apps/fixture.app",
                  "mobile: activateApp");
    }
    requests.clear();
    controller.close();
    controller.close();
    assertThat(requests.stream().filter(r -> r.startsWith("DELETE")).count()).isEqualTo(1);
    assertThat(requests.stream().filter(r -> r.contains("mobile: removeApp")).count())
        .isEqualTo(policy == LifecyclePolicy.REINSTALL ? 1 : 0);
  }

  @Test
  @DisplayName("installs and explicitly uninstalls only the configured server reference")
  void install() {
    var settings = settings();
    settings.setApplicationMode(ApplicationMode.PACKAGED);
    settings.setAppReference(
        new AppReference(
            ReferenceKind.SERVER_PATH, "/apps/fixture.app", MobileDeviceKind.SIMULATOR));
    initialize(settings);
    controller.install();
    controller.uninstall();
    assertThat(String.join("\n", requests))
        .contains(
            "mobile: installApp", "/apps/fixture.app", "mobile: removeApp", "com.example.fixture");
  }

  @Test
  @DisplayName("selects the requested WebView and invalidates handles even after native return")
  void hybrid() {
    var settings = settings();
    settings.setExecutionMode(MobileExecutionMode.HYBRID);
    initialize(settings);
    var nativeElement = controller.find(new AppleLocator(Kind.ACCESSIBILITY, "submit"));
    controller.selectWebView("WEBVIEW_B");
    assertThat(context).isEqualTo("WEBVIEW_B");
    assertThrows(StaleElementReferenceException.class, () -> controller.tap(nativeElement));
    var webElement = controller.find(new AppleLocator(Kind.CSS, "button"));
    controller.tap(webElement);
    controller.returnToNative();
    assertThat(context).isEqualTo("NATIVE_APP");
    assertThrows(StaleElementReferenceException.class, () -> controller.tap(webElement));
    assertThrows(StaleElementReferenceException.class, () -> controller.tap(nativeElement));
    controller.tap(controller.find(new AppleLocator(Kind.ACCESSIBILITY, "submit")));
  }

  @Test
  @DisplayName("bounds missing context discovery and preserves interruption")
  void timeout() {
    var settings = settings();
    settings.setExecutionMode(MobileExecutionMode.HYBRID);
    initialize(settings);
    missing = true;
    assertTimeoutPreemptively(
        Duration.ofSeconds(2),
        () -> {
          var failure =
              assertThrows(
                  IllegalStateException.class, () -> controller.selectWebView("WEBVIEW_A"));
          assertThat(failure)
              .hasMessageContaining("inspectability")
              .hasMessageContaining("Web Inspector");
        });
    assertThat(context).isEqualTo("NATIVE_APP");
    Thread.currentThread().interrupt();
    try {
      assertThrows(IllegalStateException.class, () -> controller.selectWebView("WEBVIEW_A"));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @DisplayName("sanitizes stale and provider failures and never replays a click")
  void failures() {
    initialize(settings());
    var handle = controller.find(new AppleLocator(Kind.ACCESSIBILITY, "submit"));
    stale = true;
    var failure = assertThrows(StaleElementReferenceException.class, () -> controller.tap(handle));
    assertThat(failure).hasMessageNotContaining("CANARY").hasCause(null);
    assertThat(requests.stream().filter(r -> r.contains("/click")).count()).isEqualTo(1);
    stale = false;
    reject = true;
    assertThat(assertThrows(AppleControllerException.class, controller::activate))
        .hasMessageNotContaining("CANARY")
        .hasCause(null);
  }

  @Test
  @DisplayName("cleanup attempts quit after application termination fails")
  void cleanupFailure() {
    var settings = settings();
    settings.setTerminateAppOnClose(true);
    initialize(settings);
    reject = true;
    assertThrows(AppleControllerException.class, controller::close);
    controller.close();
    assertThat(requests.stream().filter(r -> r.startsWith("DELETE")).count()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(AndroidOperation.class)
  @DisplayName("rejects Android extensions without opening a session")
  void android(AndroidOperation operation) {
    controller = new DefaultAppleController("passive", settings(), null);
    assertThat(
            assertThrows(
                UnsupportedOperationException.class, () -> controller.androidOperation(operation)))
        .hasMessageContaining("unsupported on Apple");
    assertThat(requests).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, 600001})
  @DisplayName("rejects unbounded backgrounding and gestures before protocol mutation")
  void duration(long millis) {
    initialize(settings());
    requests.clear();
    assertThrows(
        IllegalArgumentException.class, () -> controller.background(Duration.ofMillis(millis)));
    assertThrows(
        IllegalArgumentException.class,
        () -> controller.swipe(0, 0, 10, 10, Duration.ofMillis(millis)));
    assertThat(requests).isEmpty();
  }

  @Test
  @DisplayName("keeps Safari navigation separate and rejects native application operations")
  void safari() {
    var settings = settings();
    settings.setExecutionMode(MobileExecutionMode.SAFARI);
    settings.setBundleId(null);
    settings.setTerminateAppOnClose(null);
    initialize(settings);
    context = "WEBVIEW_SAFARI";
    controller.navigate("https://example.test/order");
    controller.tap(controller.find(new AppleLocator(Kind.CSS, "button")));
    assertThrows(UnsupportedOperationException.class, controller::install);
    assertThrows(UnsupportedOperationException.class, controller::launch);
    assertThrows(UnsupportedOperationException.class, () -> controller.selectWebView("WEBVIEW_A"));
    assertThat(String.join("\n", requests))
        .contains("Safari", "https://example.test/order", "css selector");
  }

  @Test
  @DisplayName("rejects simulator permission changes on physical devices")
  void physical() {
    var settings = settings();
    settings.setDeviceKind(MobileDeviceKind.PHYSICAL);
    settings.setDeviceId("fixture");
    initialize(settings);
    requests.clear();
    assertThrows(UnsupportedOperationException.class, () -> controller.grantPermission("contacts"));
    assertThrows(
        UnsupportedOperationException.class, () -> controller.revokePermission("contacts"));
    assertThat(requests).isEmpty();
  }
}
