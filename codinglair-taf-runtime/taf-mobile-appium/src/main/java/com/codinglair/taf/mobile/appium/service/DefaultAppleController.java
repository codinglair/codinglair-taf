package com.codinglair.taf.mobile.appium.service;

import com.codinglair.taf.mobile.ApplicationMode;
import com.codinglair.taf.mobile.MobileDeviceFamily;
import com.codinglair.taf.mobile.MobileDeviceKind;
import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.MobileOrientation;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.evidence.AppleEvidence;
import com.codinglair.taf.mobile.appium.exception.AppleControllerException;
import com.codinglair.taf.mobile.appium.exception.AppleTransportFailure;
import com.codinglair.taf.mobile.appium.platform.AppleLocator;
import com.codinglair.taf.mobile.appium.platform.ApplePlatformStrategy;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import io.appium.java_client.appmanagement.ApplicationState;
import io.appium.java_client.ios.IOSDriver;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

/** One lazy driver per controller. Serialized lifecycle prevents close/initialize races. */
public final class DefaultAppleController implements AppleController {
  private final ControllerIdentity identity;
  private final AppleControllerSettings settings;
  private ControllerState state = ControllerState.NEW;
  private IOSDriver driver;
  private long generation;
  private boolean installedByController;
  private final AppleResourceReservations reservations;
  private final AppleTransportSecurity security;
  private AutoCloseable reservation;
  private boolean uncertain;
  private String providerOwnedSession;
  private ControllerContext context;
  private AppleEvidence evidence;

  public DefaultAppleController(
      String name, AppleControllerSettings base, AppleControllerSettings instance) {
    this(name, base, instance, new AppleResourceReservations());
  }

  /**
   * Share the coordinator across factories that consume the same externally allocated resources.
   */
  public DefaultAppleController(
      String name,
      AppleControllerSettings base,
      AppleControllerSettings instance,
      AppleResourceReservations reservations) {
    this(name, base, instance, reservations, null);
  }

  /**
   * Governed callers supply a caller-bound policy; trusted standalone callers use exact
   * configuration.
   */
  public DefaultAppleController(
      String name,
      AppleControllerSettings base,
      AppleControllerSettings instance,
      AppleResourceReservations reservations,
      AppleTransportSecurity security) {
    identity = new ControllerIdentity(AppleController.class, name);
    // Passive construction: validation and network access occur only at initialization.
    settings = snapshot(base, instance);
    this.reservations = Objects.requireNonNull(reservations);
    this.security = security == null ? AppleTransportSecurity.trusted(settings, null) : security;
  }

  private static AppleControllerSettings snapshot(
      AppleControllerSettings base, AppleControllerSettings instance) {
    return AppleControllerSettings.merge(base, instance, null);
  }

  @Override
  public ControllerIdentity identity() {
    return identity;
  }

  @Override
  public synchronized ControllerState state() {
    return state;
  }

  @Override
  public MobileDeviceFamily family() {
    return settings.family();
  }

  @Override
  public synchronized void initialize(ControllerContext context) {
    Objects.requireNonNull(context);
    if (state != ControllerState.NEW)
      throw new IllegalStateException("Apple controller cannot initialize from " + state);
    this.context = context;
    state = ControllerState.INITIALIZING;
    try {
      if (Thread.currentThread().isInterrupted())
        throw new IllegalStateException("Apple initialization cancelled");
      providerOwnedSession = AppleAllocation.apply(settings, context.environments());
      settings.validate();
      security.requireSettings(settings);
      var readiness = AppleReadiness.inspect(settings);
      if (readiness.status() == HealthResult.Status.UNAVAILABLE)
        throw new IllegalStateException("Apple prerequisites unavailable");
      reservation = reservations.acquire(settings);
      driver = new ApplePlatformStrategy().create(settings, security, context.sessionId());
      providerOwnedSession = driver.getSessionId().toString();
      if (Thread.currentThread().isInterrupted())
        throw new IllegalStateException("Apple initialization cancelled");
      state = ControllerState.READY;
      evidence = new AppleEvidence(settings, context);
      evidence.start(driver);
    } catch (RuntimeException failure) {
      throw failedInitialization(failure);
    }
  }

  private AppleControllerException failedInitialization(RuntimeException failure) {
    state = ControllerState.FAILED;
    uncertain = driver == null && reservation != null && remoteUncertain(failure);
    var primary =
        new AppleControllerException(
            uncertain
                ? "initialize: remote session outcome uncertain; reconcile only identifiable owned sessions"
                : "initialize");
    cleanupPartialDriver(primary);
    reconcileOwnedSession(primary);
    if (!uncertain) releaseReservation(primary);
    return primary;
  }

  private void cleanupPartialDriver(AppleControllerException primary) {
    if (driver == null) return;
    try {
      driver.quit();
    } catch (RuntimeException _) {
      uncertain = true;
      primary.addSuppressed(new AppleControllerException("partial initialization cleanup"));
    } finally {
      driver = null;
    }
  }

  private void reconcileOwnedSession(AppleControllerException primary) {
    if (!uncertain || providerOwnedSession == null) return;
    try {
      AppleAllocation.cleanup(settings, providerOwnedSession, security, context.sessionId());
      // A pending create can complete after a successful DELETE/404. Keep quarantine until the
      // provider/operator authoritatively reconciles the allocation.
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      primary.addSuppressed(new AppleControllerException("owned session cleanup cancelled"));
    } catch (Exception _) {
      primary.addSuppressed(new AppleControllerException("owned session cleanup"));
    }
  }

  private static boolean transportUncertain(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof IOException
          || current instanceof TimeoutException
          || current instanceof CancellationException) return true;
      if (current instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        return true;
      }
    }
    return Thread.currentThread().isInterrupted();
  }

  private static boolean remoteUncertain(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause())
      if (current instanceof AppleTransportFailure transport)
        return transport.connectionAttempted();
    return !(failure instanceof SessionNotCreatedException) || transportUncertain(failure);
  }

  private void releaseReservation(RuntimeException primary) {
    if (reservation == null) return;
    try {
      reservation.close();
    } catch (Exception _) {
      primary.addSuppressed(new AppleControllerException("allocation release"));
    } finally {
      reservation = null;
    }
  }

  @Override
  public synchronized IOSDriver nativeDriver() {
    if (state != ControllerState.READY)
      throw new IllegalStateException("Apple controller is not ready");
    return driver;
  }

  @Override
  public synchronized HealthResult health() {
    return switch (state) {
      case READY ->
          new HealthResult(
              HealthResult.Status.HEALTHY,
              "Apple session is ready",
              Map.of("family", family().name()));
      case FAILED, CLOSED ->
          new HealthResult(
              HealthResult.Status.UNAVAILABLE,
              "Apple session state is " + state,
              uncertain
                  ? Map.of(
                      "ownership",
                      "Remote session outcome uncertain; provider/operator must reconcile only owned sessions before allocation reuse")
                  : Map.of());
      default -> AppleReadiness.inspect(settings);
    };
  }

  @Override
  public synchronized Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    Objects.requireNonNull(reason);
    if (state != ControllerState.READY) return Stream.empty();
    var artifacts = evidence.collect(driver, reason);
    artifacts.forEach(context.artifacts()::addArtifact);
    if (settings.getRequireEvidence()
        && (artifacts.isEmpty()
            || artifacts.stream()
                .anyMatch(
                    artifact ->
                        artifact.type().equals("diagnostic")
                            && artifact.name().endsWith("-availability")
                            && artifact.content().contains("outcome=")
                            && !artifact.content().endsWith("outcome=available"))))
      throw new AppleControllerException(
          "required evidence unavailable; inspect artifact availability");
    return artifacts.stream();
  }

  @Override
  public synchronized void close() {
    if (state == ControllerState.CLOSED) return;
    boolean evidenceFailed = finalizeEvidence();
    state = ControllerState.CLOSED;
    IOSDriver owned = driver;
    driver = null;
    generation++;
    if (owned != null) {
      AppleControllerException failure = teardownOwnedDriver(owned, evidenceFailed);
      if (failure != null) throw failure;
    }
    if (!uncertain && owned == null) {
      var cleanup = new AppleControllerException("allocation release");
      releaseReservation(cleanup);
      if (cleanup.getSuppressed().length > 0) throw cleanup;
    }
  }

  private boolean finalizeEvidence() {
    if (driver == null || context == null) return false;
    try (var artifacts = collectArtifacts(ArtifactReason.DIAGNOSTIC)) {
      // Collection publishes once; close the returned stream before application teardown.
      return false;
    } catch (RuntimeException _) {
      return true;
    }
  }

  private AppleControllerException teardownOwnedDriver(IOSDriver owned, boolean failed) {
    try {
      if (settings.getExecutionMode() != MobileExecutionMode.SAFARI
          && settings.getTerminateAppOnClose()
          && settings.getBundleId() != null) owned.terminateApp(settings.getBundleId());
    } catch (RuntimeException _) {
      failed = true;
    }
    try {
      if (installedByController && settings.getUninstallPackagedAppOnClose())
        owned.removeApp(bundle());
    } catch (RuntimeException _) {
      failed = true;
    }
    try {
      owned.quit();
    } catch (RuntimeException _) {
      failed = true;
      uncertain = true;
    }
    var cleanup = new AppleControllerException("close");
    if (!uncertain) releaseReservation(cleanup);
    return failed || cleanup.getSuppressed().length > 0 ? cleanup : null;
  }

  private <T> T operation(String name, Supplier<T> action) {
    nativeDriver();
    if (Thread.currentThread().isInterrupted())
      throw new IllegalStateException("Apple operation cancelled");
    try {
      return action.get();
    } catch (StaleElementReferenceException _) {
      throw new StaleElementReferenceException(
          "Apple element is stale; resolve its locator again; action was not replayed");
    } catch (RuntimeException _) {
      throw new AppleControllerException(name);
    }
  }

  private void action(String name, Runnable action) {
    operation(
        name,
        () -> {
          action.run();
          return null;
        });
  }

  private String bundle() {
    if (settings.getExecutionMode() == MobileExecutionMode.SAFARI)
      throw new UnsupportedOperationException(
          "Application lifecycle is unavailable in Safari mode");
    if (settings.getBundleId() == null || settings.getBundleId().isBlank())
      throw new IllegalStateException("This operation requires a configured bundle-id");
    return settings.getBundleId();
  }

  private static void bounded(Duration duration) {
    if (duration == null
        || duration.compareTo(Duration.ofMillis(1)) < 0
        || duration.compareTo(Duration.ofMinutes(10)) > 0)
      throw new IllegalArgumentException(
          "Duration must be between one millisecond and ten minutes");
  }

  private WebElement element(AppleElement handle) {
    nativeDriver();
    Objects.requireNonNull(handle);
    if (handle.owner != this || handle.generation != generation)
      throw new StaleElementReferenceException(
          "Apple element belongs to another session or context; resolve again");
    return handle.element;
  }

  @Override
  public synchronized AppleElement find(AppleLocator locator) {
    Objects.requireNonNull(locator);
    String context = operation("get-context", () -> driver.getContext());
    boolean web = !"NATIVE_APP".equals(context);
    if ((locator.kind() == AppleLocator.Kind.CSS && !web)
        || (web
            && locator.kind() != AppleLocator.Kind.CSS
            && locator.kind() != AppleLocator.Kind.XPATH))
      throw new IllegalArgumentException(
          "Locator strategy is incompatible with the active context");
    return new AppleElement(
        this, generation, operation("find", () -> driver.findElement(locator.by())));
  }

  @Override
  @ControllerAction("Tap Apple element")
  public synchronized void tap(AppleElement handle) {
    WebElement target = element(handle);
    action("tap", target::click);
  }

  @Override
  @ControllerAction("Type into Apple element")
  public synchronized void type(AppleElement handle, String text) {
    WebElement target = element(handle);
    Objects.requireNonNull(text);
    action("type", () -> target.sendKeys(text));
  }

  @Override
  public synchronized String text(AppleElement handle) {
    WebElement target = element(handle);
    return operation("text", target::getText);
  }

  @Override
  @ControllerAction("Swipe on Apple device")
  public synchronized void swipe(int sx, int sy, int ex, int ey, Duration duration) {
    bounded(duration);
    if (sx < 0 || sy < 0 || ex < 0 || ey < 0)
      throw new IllegalArgumentException("Coordinates must be nonnegative");
    var finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
    var sequence = new Sequence(finger, 0);
    sequence.addAction(
        finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), sx, sy));
    sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
    sequence.addAction(finger.createPointerMove(duration, PointerInput.Origin.viewport(), ex, ey));
    sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
    action("swipe", () -> driver.perform(List.of(sequence)));
  }

  @Override
  @ControllerAction("Long press Apple element")
  public synchronized void longPress(AppleElement handle, Duration duration) {
    bounded(duration);
    WebElement target = element(handle);
    var finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
    var sequence = new Sequence(finger, 0);
    sequence.addAction(
        finger.createPointerMove(Duration.ZERO, PointerInput.Origin.fromElement(target), 0, 0));
    sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
    sequence.addAction(new Pause(finger, duration));
    sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
    action("long-press", () -> driver.perform(List.of(sequence)));
  }

  @Override
  @ControllerAction("Set Apple orientation")
  public synchronized void setOrientation(MobileOrientation orientation) {
    Objects.requireNonNull(orientation);
    action(
        "orientation",
        () ->
            driver.rotate(
                orientation == MobileOrientation.PORTRAIT
                    ? ScreenOrientation.PORTRAIT
                    : ScreenOrientation.LANDSCAPE));
  }

  private void installConfigured() {
    if (settings.getExecutionMode() == MobileExecutionMode.SAFARI
        || settings.getApplicationMode() != ApplicationMode.PACKAGED)
      throw new UnsupportedOperationException("Installation requires packaged native/hybrid mode");
    bundle(); // Identity is needed for ownership-limited cleanup.
    generation++;
    action("install", () -> driver.installApp(settings.getAppReference().value()));
    installedByController = true;
  }

  @Override
  @ControllerAction("Install Apple application")
  public synchronized void install() {
    installConfigured();
  }

  @Override
  @ControllerAction("Uninstall Apple application")
  public synchronized void uninstall() {
    String id = bundle();
    generation++;
    action("uninstall", () -> driver.removeApp(id));
    installedByController = false;
  }

  @Override
  @ControllerAction("Launch Apple application")
  public synchronized void launch() {
    String id = bundle();
    generation++;
    action("launch", () -> driver.executeScript("mobile: launchApp", Map.of("bundleId", id)));
  }

  @Override
  @ControllerAction("Activate Apple application")
  public synchronized void activate() {
    String id = bundle();
    generation++;
    action("activate", () -> driver.activateApp(id));
  }

  @Override
  public synchronized ApplicationState queryAppState() {
    String id = bundle();
    return operation("query-app-state", () -> driver.queryAppState(id));
  }

  @Override
  @ControllerAction("Terminate Apple application")
  public synchronized void terminate() {
    String id = bundle();
    generation++;
    action("terminate", () -> driver.terminateApp(id));
  }

  @Override
  @ControllerAction("Reset Apple application using declared policy")
  public synchronized void reset() {
    String id = bundle();
    nativeDriver();
    switch (settings.getLifecyclePolicy()) {
      case REUSE -> {}
      case RELAUNCH -> {
        generation++;
        action("reset-terminate", () -> driver.terminateApp(id));
        action("reset-activate", () -> driver.activateApp(id));
      }
      case REINSTALL -> {
        generation++;
        action("reset-remove", () -> driver.removeApp(id));
        installedByController = false;
        installConfigured();
        action("reset-activate", () -> driver.activateApp(id));
      }
    }
  }

  @Override
  @ControllerAction("Background Apple application")
  public synchronized void background(Duration duration) {
    bounded(duration);
    bundle();
    generation++;
    action(
        "background",
        () ->
            driver.executeScript(
                "mobile: backgroundApp", Map.of("seconds", duration.toNanos() / 1_000_000_000.0)));
  }

  private static String uri(String value) {
    try {
      URI parsed = URI.create(value);
      if (!parsed.isAbsolute() || parsed.getUserInfo() != null)
        throw new IllegalArgumentException();
      return value;
    } catch (RuntimeException _) {
      throw new IllegalArgumentException("An absolute URI without credentials is required");
    }
  }

  @Override
  @ControllerAction("Open Apple deep link")
  public synchronized void openDeepLink(String value) {
    String url = uri(value);
    String id = bundle();
    generation++;
    action(
        "deep-link",
        () -> driver.executeScript("mobile: deepLink", Map.of("url", url, "bundleId", id)));
  }

  private void permission(String permission, String value) {
    if (settings.getDeviceKind() != MobileDeviceKind.SIMULATOR)
      throw new UnsupportedOperationException(
          "Permission configuration requires a simulator; use system dialogs on physical devices");
    if (permission == null || !permission.matches("[a-z][a-z-]{0,63}"))
      throw new IllegalArgumentException("A simulator permission name is required");
    String id = bundle();
    generation++;
    action(
        "permission",
        () ->
            driver.executeScript(
                "mobile: setPermission",
                Map.of("bundleId", id, "access", Map.of(permission, value))));
  }

  @Override
  @ControllerAction("Grant Apple simulator permission")
  public synchronized void grantPermission(String permission) {
    permission(permission, "yes");
  }

  @Override
  @ControllerAction("Revoke Apple simulator permission")
  public synchronized void revokePermission(String permission) {
    permission(permission, "no");
  }

  @Override
  @ControllerAction("Accept Apple system dialog")
  public synchronized void acceptDialog() {
    action("accept-dialog", () -> driver.switchTo().alert().accept());
  }

  @Override
  @ControllerAction("Dismiss Apple system dialog")
  public synchronized void dismissDialog() {
    action("dismiss-dialog", () -> driver.switchTo().alert().dismiss());
  }

  @Override
  @ControllerAction("Open Apple notification UI")
  public synchronized void openNotifications() {
    String context = operation("get-context", () -> driver.getContext());
    if (!"NATIVE_APP".equals(context))
      throw new IllegalStateException("Notification UI requires native context");
    generation++;
    action(
        "notifications",
        () -> {
          var size = driver.manage().window().getSize();
          var finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
          var sequence = new Sequence(finger, 0);
          sequence.addAction(
              finger.createPointerMove(
                  Duration.ZERO, PointerInput.Origin.viewport(), size.width / 2, 0));
          sequence.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
          sequence.addAction(
              finger.createPointerMove(
                  Duration.ofMillis(500),
                  PointerInput.Origin.viewport(),
                  size.width / 2,
                  size.height * 3 / 4));
          sequence.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
          driver.perform(List.of(sequence));
        });
  }

  @Override
  public synchronized Set<String> contexts() {
    return Set.copyOf(operation("contexts", () -> driver.getContextHandles()));
  }

  @Override
  @ControllerAction("Select explicit Apple WebView")
  public synchronized void selectWebView(String selected) {
    if (settings.getExecutionMode() != MobileExecutionMode.HYBRID)
      throw new UnsupportedOperationException("WebView discovery requires HYBRID mode");
    if (selected == null || !selected.startsWith("WEBVIEW") || selected.length() > 256)
      throw new IllegalArgumentException("An explicit WebView context identifier is required");
    long deadline = System.nanoTime() + settings.getContextTimeout().toNanos();
    do {
      if (contexts().contains(selected)) {
        generation++;
        action("select-webview", () -> driver.context(selected));
        return;
      }
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) break;
      try {
        TimeUnit.NANOSECONDS.sleep(Math.min(remaining, Duration.ofMillis(50).toNanos()));
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Apple context discovery cancelled");
      }
    } while (System.nanoTime() < deadline);
    throw new IllegalStateException(
        "Expected Apple WebView unavailable within context-timeout; verify WKWebView inspectability, Safari Web Inspector and device/host authorization");
  }

  @Override
  @ControllerAction("Return to Apple native context")
  public synchronized void returnToNative() {
    generation++;
    action("native-context", () -> driver.context("NATIVE_APP"));
  }

  @Override
  @ControllerAction("Navigate mobile Safari or Apple WebView")
  public synchronized void navigate(String value) {
    String url = uri(value);
    if ("NATIVE_APP".equals(operation("get-context", () -> driver.getContext())))
      throw new IllegalStateException("Navigation requires Safari or a selected WebView");
    generation++;
    action("navigate", () -> driver.get(url));
  }
}
