package com.codinglair.taf.mobile.appium;

import com.codinglair.taf.mobile.*;
import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;

final class DefaultAndroidController implements AndroidController {
  private final ControllerIdentity identity;
  private final AndroidControllerSettings settings;
  private final AppiumAndroidSessionFactory sessions;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private ControllerContext context;
  private AndroidDriver driver;
  private boolean installedByController;
  private boolean videoStarted;

  DefaultAndroidController(
      String name, AndroidControllerSettings settings, AppiumAndroidSessionFactory sessions) {
    identity = new ControllerIdentity(AndroidController.class, name);
    this.settings = Objects.requireNonNull(settings);
    this.sessions = Objects.requireNonNull(sessions);
  }

  @Override
  public ControllerIdentity identity() {
    return identity;
  }

  @Override
  public ControllerState state() {
    return state.get();
  }

  @Override
  public MobilePlatform platform() {
    return MobilePlatform.ANDROID;
  }

  @Override
  public void initialize(ControllerContext value) {
    if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING))
      throw new IllegalStateException("Controller cannot initialize from " + state.get());
    context = Objects.requireNonNull(value);
    try {
      settings.validate("taf.mobile.android.controllers." + identity.name());
      driver = sessions.create(settings);
      if (settings.isVideo()) {
        driver.startRecordingScreen();
        videoStarted = true;
      }
      state.set(ControllerState.READY);
    } catch (Throwable failure) {
      state.set(ControllerState.FAILED);
      Throwable cleanup = cleanup(failure);
      throw failure(
          "initialize",
          "verify Appium endpoint, UiAutomator2, device authorization, and application configuration",
          cleanup);
    }
  }

  @Override
  public HealthResult health() {
    return switch (state.get()) {
      case READY ->
          new HealthResult(
              HealthResult.Status.HEALTHY,
              "Android Appium session is ready",
              Map.of("platform", "ANDROID", "mode", settings.getDeviceMode().name()));
      case FAILED ->
          new HealthResult(
              HealthResult.Status.UNAVAILABLE,
              "Android Appium initialization failed",
              Map.of("correctiveAction", "Verify Appium, UiAutomator2, and device authorization"));
      case CLOSED ->
          new HealthResult(
              HealthResult.Status.UNAVAILABLE, "Android controller is closed", Map.of());
      default -> HealthResult.unknown("Android controller has not initialized");
    };
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    ensureInitialized();
    var artifacts = new ArrayList<TestArtifact>();
    try {
      if (reason == ArtifactReason.FAILURE && settings.isScreenshotOnFailure())
        artifacts.add(binary("android-screenshot.png", "screenshot", screenshot(), "image/png"));
      if (reason == ArtifactReason.FAILURE && settings.isPageSourceOnFailure())
        artifacts.add(
            text("android-page-source.xml", "page-source", pageSource(), "application/xml"));
      if (settings.isDeviceLogs()) {
        var lines = new ArrayList<String>();
        driver.manage().logs().get("logcat").forEach(entry -> lines.add(entry.toString()));
        if (!lines.isEmpty())
          artifacts.add(
              text("android-logcat.txt", "device-log", String.join("\n", lines), "text/plain"));
      }
      if (videoStarted) {
        String encoded = driver.stopRecordingScreen();
        videoStarted = false;
        if (!encoded.isBlank())
          artifacts.add(encoded("android-video.mp4", "video", encoded, "video/mp4"));
      }
    } catch (Throwable failure) {
      throw failure("evidence", "verify the device supports the requested evidence type", failure);
    }
    artifacts.forEach(context.artifacts()::addArtifact);
    return artifacts.stream();
  }

  @Override
  public void close() {
    if (state.getAndSet(ControllerState.CLOSED) == ControllerState.CLOSED) return;
    Throwable failure = cleanup(null);
    if (failure != null)
      throw failure(
          "close", "verify the device is reachable so declared app state can be restored", failure);
  }

  private Throwable cleanup(Throwable prior) {
    if (driver == null) return prior;
    if (videoStarted)
      try {
        driver.stopRecordingScreen();
      } catch (Throwable e) {
        prior = append(prior, e);
      } finally {
        videoStarted = false;
      }
    if (settings.isTerminateAppOnClose())
      try {
        driver.terminateApp(settings.getAppPackage());
      } catch (Throwable e) {
        prior = append(prior, e);
      }
    if (installedByController && settings.isUninstallPackagedAppOnClose())
      try {
        driver.removeApp(settings.getAppPackage());
      } catch (Throwable e) {
        prior = append(prior, e);
      }
    try {
      driver.quit();
    } catch (Throwable e) {
      prior = append(prior, e);
    }
    driver = null;
    return prior;
  }

  private static Throwable append(Throwable prior, Throwable current) {
    if (prior == null) return current;
    prior.addSuppressed(current);
    return prior;
  }

  private AndroidControllerException failure(String operation, String correction, Throwable cause) {
    return new AndroidControllerException(operation, correction, cause);
  }

  private void ensureReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("Android controller is not READY: " + state.get());
  }

  private void ensureInitialized() {
    if (state.get() == ControllerState.NEW || state.get() == ControllerState.INITIALIZING)
      throw new IllegalStateException("Android controller has not completed initialization");
  }

  private TestArtifact text(String name, String type, String value, String contentType) {
    String safe = context.artifacts().redact(value);
    return TestArtifact.of(
        name, type, safe, contentType, null, context.artifacts().computeHash(safe));
  }

  private TestArtifact binary(String name, String type, byte[] value, String contentType) {
    requireVisual(type);
    return encoded(name, type, Base64.getEncoder().encodeToString(value), contentType);
  }

  private TestArtifact encoded(String name, String type, String value, String contentType) {
    requireVisual(type);
    return TestArtifact.of(
        name, type, value, contentType, null, context.artifacts().computeHash(value));
  }

  private void requireVisual(String type) {
    if (!settings.isAllowVisualArtifacts())
      throw new IllegalStateException(type + " capture requires allow-visual-artifacts=true");
  }

  @Override
  public AndroidDriver nativeDriver() {
    ensureReady();
    return driver;
  }

  @Override
  public WebElement find(String selector) {
    return nativeDriver()
        .findElement(AppiumBy.androidUIAutomator(Objects.requireNonNull(selector)));
  }

  @Override
  @ControllerAction("Tap mobile element")
  public void tap(WebElement element) {
    element.click();
  }

  @Override
  @ControllerAction("Type into mobile element")
  public void type(WebElement element, String text) {
    element.sendKeys(text);
  }

  @Override
  @ControllerAction("Swipe on mobile device")
  public void swipe(int sx, int sy, int ex, int ey, Duration duration) {
    if (duration == null || duration.isZero() || duration.isNegative())
      throw new IllegalArgumentException("Swipe duration must be positive");
    double distance = Math.hypot(ex - sx, ey - sy);
    execute(
        "mobile: dragGesture",
        Map.of(
            "startX",
            sx,
            "startY",
            sy,
            "endX",
            ex,
            "endY",
            ey,
            "speed",
            Math.max(1, (int) (distance * 1000 / duration.toMillis()))));
  }

  @Override
  @ControllerAction("Long press mobile element")
  public void longPress(WebElement element, Duration duration) {
    if (!(element instanceof RemoteWebElement remote))
      throw new IllegalArgumentException("Long press requires a remote Appium element");
    if (duration == null || duration.isZero() || duration.isNegative())
      throw new IllegalArgumentException("Long-press duration must be positive");
    execute(
        "mobile: longClickGesture",
        Map.of("elementId", remote.getId(), "duration", duration.toMillis()));
  }

  @Override
  @ControllerAction("Set mobile orientation")
  public void setOrientation(MobileOrientation value) {
    nativeDriver()
        .rotate(
            value == MobileOrientation.PORTRAIT
                ? ScreenOrientation.PORTRAIT
                : ScreenOrientation.LANDSCAPE);
  }

  @Override
  @ControllerAction("Install mobile application")
  public void install(Path app) {
    Path safe = Objects.requireNonNull(app, "app").toAbsolutePath().normalize();
    if (!java.nio.file.Files.isRegularFile(safe))
      throw new IllegalArgumentException("Application package must be an existing regular file");
    nativeDriver().installApp(safe.toString());
    installedByController = true;
  }

  @Override
  @ControllerAction("Launch mobile application")
  public void launch() {
    nativeDriver().activateApp(settings.getAppPackage());
  }

  @Override
  @ControllerAction("Reset mobile application")
  public void reset() {
    execute("mobile: clearApp", Map.of("appId", settings.getAppPackage()));
    launch();
  }

  @Override
  @ControllerAction("Background mobile application")
  public void background(Duration duration) {
    nativeDriver().runAppInBackground(duration);
  }

  @Override
  @ControllerAction("Terminate mobile application")
  public void terminate() {
    nativeDriver().terminateApp(settings.getAppPackage());
  }

  @Override
  @ControllerAction("Open mobile deep link")
  public void openDeepLink(String uri) {
    execute("mobile: deepLink", Map.of("url", uri, "package", settings.getAppPackage()));
  }

  @Override
  @ControllerAction("Grant mobile permission")
  public void grantPermission(String permission) {
    permission("grant", permission);
  }

  @Override
  @ControllerAction("Revoke mobile permission")
  public void revokePermission(String permission) {
    permission("revoke", permission);
  }

  private void permission(String action, String permission) {
    execute(
        "mobile: changePermissions",
        Map.of(
            "action",
            action,
            "appPackage",
            settings.getAppPackage(),
            "permissions",
            List.of(permission)));
  }

  @Override
  @ControllerAction("Accept system dialog")
  public void acceptDialog() {
    nativeDriver().switchTo().alert().accept();
  }

  @Override
  @ControllerAction("Dismiss system dialog")
  public void dismissDialog() {
    nativeDriver().switchTo().alert().dismiss();
  }

  @Override
  @ControllerAction("Open Android notifications")
  public void openNotifications() {
    nativeDriver().openNotifications();
  }

  @Override
  @ControllerAction("Capture mobile screenshot")
  public byte[] screenshot() {
    requireVisual("screenshot");
    return nativeDriver().getScreenshotAs(OutputType.BYTES);
  }

  @Override
  public String pageSource() {
    return nativeDriver().getPageSource();
  }

  private Object execute(String command, Map<String, ?> args) {
    return nativeDriver().executeScript(command, args);
  }
}
