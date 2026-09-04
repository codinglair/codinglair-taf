package com.codinglair.taf.web.playwright;

import com.codinglair.taf.runtime.core.controller.*;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import com.codinglair.taf.runtime.core.reporting.annotation.ControllerAction;
import com.microsoft.playwright.*;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class DefaultPlaywrightController implements PlaywrightController {
  private final ControllerIdentity identity;
  private final PlaywrightControllerSettings properties;
  private final AtomicReference<ControllerState> state = new AtomicReference<>(ControllerState.NEW);
  private final List<String> consoleErrors = new CopyOnWriteArrayList<>();
  private final List<String> networkErrors = new CopyOnWriteArrayList<>();
  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private ControllerContext controllerContext;
  private Playwright playwright;
  private Browser browser;
  private BrowserContext browserContext;
  private Page page;
  private Path tracePath;

  DefaultPlaywrightController(String name, PlaywrightControllerSettings properties) {
    this.identity = new ControllerIdentity(PlaywrightController.class, name);
    this.properties = Objects.requireNonNull(properties);
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
  public void initialize(ControllerContext context) {
    if (!state.compareAndSet(ControllerState.NEW, ControllerState.INITIALIZING))
      throw new IllegalStateException("Controller cannot initialize from " + state.get());
    controllerContext = Objects.requireNonNull(context);
    try {
      properties.validate("taf.web.playwright.controllers." + identity.name());
      playwright = Playwright.create();
      BrowserType type = browserType(playwright);
      if (properties.getMode() == PlaywrightProperties.Mode.REMOTE) {
        browser = type.connect(properties.getRemoteEndpoint().toString());
      } else {
        BrowserType.LaunchOptions launch =
            new BrowserType.LaunchOptions().setHeadless(properties.isHeadless());
        if (properties.getChannel() != null && !properties.getChannel().isBlank())
          launch.setChannel(properties.getChannel());
        browser = type.launch(launch);
      }
      Browser.NewContextOptions options =
          new Browser.NewContextOptions()
              .setViewportSize(properties.getViewportWidth(), properties.getViewportHeight());
      if (properties.getStorageState() != null)
        options.setStorageStatePath(properties.getStorageState());
      if (properties.getEvidence().isVideo()) {
        requireVisualPolicy("video");
        options.setRecordVideoDir(evidenceDirectory());
      }
      browserContext = browser.newContext(options);
      if (properties.getEvidence().isTrace()) {
        browserContext
            .tracing()
            .start(
                new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true)
                    .setSources(false));
      }
      page = browserContext.newPage();
      page.setDefaultTimeout(properties.getTimeout().toMillis());
      installEvidenceListeners(page);
      state.set(ControllerState.READY);
    } catch (Throwable failure) {
      state.set(ControllerState.FAILED);
      closeResources(failure);
      throw new PlaywrightControllerException(
          "initialize",
          "verify browser installation, channel, or remote endpoint configuration",
          failure);
    }
  }

  private BrowserType browserType(Playwright owner) {
    return switch (properties.getEngine()) {
      case CHROMIUM -> owner.chromium();
      case FIREFOX -> owner.firefox();
      case WEBKIT -> owner.webkit();
    };
  }

  private void installEvidenceListeners(Page target) {
    target.onConsoleMessage(
        message -> {
          if ("error".equalsIgnoreCase(message.type())) consoleErrors.add(message.text());
        });
    target.onRequest(requests::add);
    target.onRequestFailed(
        request ->
            networkErrors.add(request.method() + " " + request.url() + " " + request.failure()));
    target.onResponse(
        response -> {
          if (response.status() >= 400) networkErrors.add(response.status() + " " + response.url());
        });
  }

  @Override
  public HealthResult health() {
    return switch (state.get()) {
      case READY ->
          new HealthResult(
              HealthResult.Status.HEALTHY,
              "Playwright browser context is ready",
              Map.of("engine", properties.getEngine().name()));
      case FAILED ->
          new HealthResult(
              HealthResult.Status.UNAVAILABLE,
              "Playwright initialization failed",
              Map.of("correctiveAction", "Verify browser or remote endpoint configuration"));
      case CLOSED ->
          new HealthResult(
              HealthResult.Status.UNAVAILABLE, "Playwright controller is closed", Map.of());
      default -> HealthResult.unknown("Playwright controller has not initialized");
    };
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    ensureInitializedOrFailed();
    List<TestArtifact> artifacts = new ArrayList<>();
    if (reason == ArtifactReason.FAILURE && page != null) {
      if (properties.getEvidence().isScreenshotOnFailure()
          && properties.getEvidence().isAllowVisualArtifacts()) {
        artifacts.add(
            binaryArtifact("failure-screenshot.png", "screenshot", page.screenshot(), "image/png"));
      }
      if (properties.getEvidence().isDomOnFailure())
        artifacts.add(textArtifact("page.html", "dom", page.content(), "text/html"));
    }
    if (properties.getEvidence().isConsoleErrors() && !consoleErrors.isEmpty())
      artifacts.add(
          textArtifact(
              "console-errors.txt", "console", String.join("\n", consoleErrors), "text/plain"));
    if (properties.getEvidence().isNetworkErrors() && !networkErrors.isEmpty())
      artifacts.add(
          textArtifact(
              "network-errors.txt", "network", String.join("\n", networkErrors), "text/plain"));
    stopTraceIfNeeded();
    if (tracePath != null && Files.exists(tracePath))
      artifacts.add(binaryArtifact("trace.zip", "trace", read(tracePath), "application/zip"));
    if (properties.getEvidence().isVideo() && page != null && page.video() != null) {
      Video video = page.video();
      Throwable closeFailure = close(browserContext, null);
      browserContext = null;
      page = null;
      if (closeFailure != null)
        throw new PlaywrightControllerException(
            "evidence", "browser context could not be finalized for video capture", closeFailure);
      artifacts.add(binaryArtifact("video.webm", "video", read(video.path()), "video/webm"));
    }
    artifacts.forEach(controllerContext.artifacts()::addArtifact);
    return artifacts.stream();
  }

  private TestArtifact textArtifact(String name, String type, String content, String contentType) {
    String safe = controllerContext.artifacts().redact(content);
    return TestArtifact.of(
        name, type, safe, contentType, null, controllerContext.artifacts().computeHash(safe));
  }

  private TestArtifact binaryArtifact(
      String name, String type, byte[] content, String contentType) {
    requireVisualPolicy(type);
    String encoded = Base64.getEncoder().encodeToString(content);
    return TestArtifact.of(
        name, type, encoded, contentType, null, controllerContext.artifacts().computeHash(encoded));
  }

  @Override
  public void close() {
    ControllerState previous = state.getAndSet(ControllerState.CLOSED);
    if (previous == ControllerState.CLOSED) return;
    stopTraceIfNeeded();
    Throwable failure = closeResources(null);
    if (failure != null)
      throw new PlaywrightControllerException(
          "close", "one or more browser resources could not be released", failure);
  }

  private Throwable closeResources(Throwable failure) {
    failure = close(browserContext, failure);
    browserContext = null;
    failure = close(browser, failure);
    browser = null;
    failure = close(playwright, failure);
    playwright = null;
    page = null;
    return failure;
  }

  private static Throwable close(AutoCloseable closeable, Throwable failure) {
    if (closeable == null) return failure;
    try {
      closeable.close();
    } catch (Throwable current) {
      if (failure == null) return current;
      failure.addSuppressed(current);
    }
    return failure;
  }

  private void stopTraceIfNeeded() {
    if (browserContext == null || !properties.getEvidence().isTrace() || tracePath != null) return;
    try {
      tracePath = evidenceDirectory().resolve(safeName(identity.name()) + "-trace.zip");
      browserContext.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
    } catch (PlaywrightException failure) {
      networkErrors.add("Trace capture failed: " + failure.getClass().getSimpleName());
    }
  }

  private Path evidenceDirectory() {
    Path directory = properties.getEvidence().getDirectory().toAbsolutePath().normalize();
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new PlaywrightControllerException(
          "evidence", "cannot create configured evidence directory", e);
    }
    return directory;
  }

  private static String safeName(String value) {
    return value.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private void requireVisualPolicy(String artifact) {
    if (!properties.getEvidence().isAllowVisualArtifacts())
      throw new IllegalStateException(
          artifact + " capture requires taf.web.playwright.evidence.allow-visual-artifacts=true");
  }

  private static byte[] read(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new PlaywrightControllerException("evidence", "cannot read captured artifact", e);
    }
  }

  private void ensureReady() {
    if (state.get() != ControllerState.READY)
      throw new IllegalStateException("Playwright controller is not READY: " + state.get());
  }

  private void ensureInitializedOrFailed() {
    if (state.get() == ControllerState.NEW || state.get() == ControllerState.INITIALIZING)
      throw new IllegalStateException("Playwright controller has not completed initialization");
  }

  @Override
  public Page page() {
    ensureReady();
    return page;
  }

  @Override
  public BrowserContext browserContext() {
    ensureReady();
    return browserContext;
  }

  @Override
  public Browser browser() {
    ensureReady();
    return browser;
  }

  @Override
  @ControllerAction("Navigate browser")
  public void navigate(String url) {
    String requested = Objects.requireNonNull(url);
    page()
        .navigate(
            properties.getBaseUrl() == null
                ? requested
                : properties.getBaseUrl().resolve(requested).toString());
  }

  @Override
  public Locator locator(String selector) {
    return page().locator(selector);
  }

  @Override
  public Locator byRole(AriaRole role, String name) {
    return page().getByRole(role, new Page.GetByRoleOptions().setName(name));
  }

  @Override
  public Locator byText(String text) {
    return page().getByText(text);
  }

  @Override
  public Locator byLabel(String label) {
    return page().getByLabel(label);
  }

  @Override
  public Locator byTestId(String testId) {
    return page().getByTestId(testId);
  }

  @Override
  public FrameLocator frame(String selector) {
    return page().frameLocator(selector);
  }

  @Override
  @ControllerAction("Click element")
  public void click(Locator locator) {
    locator.click();
  }

  @Override
  @ControllerAction("Fill element")
  public void fill(Locator locator, String text) {
    locator.fill(text);
  }

  @Override
  @ControllerAction("Type into element")
  public void type(Locator locator, String text) {
    locator.pressSequentially(text);
  }

  @Override
  @ControllerAction("Check element")
  public void check(Locator locator) {
    locator.check();
  }

  @Override
  @ControllerAction("Uncheck element")
  public void uncheck(Locator locator) {
    locator.uncheck();
  }

  @Override
  @ControllerAction("Select option")
  public void selectOption(Locator locator, String option) {
    locator.selectOption(option);
  }

  @Override
  @ControllerAction("Press key")
  public void press(Locator locator, String key) {
    locator.press(key);
  }

  @Override
  @ControllerAction("Hover over element")
  public void hover(Locator locator) {
    locator.hover();
  }

  @Override
  @ControllerAction("Right-click element")
  public void rightClick(Locator locator) {
    locator.click(
        new Locator.ClickOptions().setButton(com.microsoft.playwright.options.MouseButton.RIGHT));
  }

  @Override
  @ControllerAction("Double-click element")
  public void doubleClick(Locator locator) {
    locator.dblclick();
  }

  @Override
  @ControllerAction("Drag and drop")
  public void dragAndDrop(Locator source, Locator target) {
    source.dragTo(target);
  }

  @Override
  @ControllerAction("Upload file")
  public void upload(Locator locator, Path file) {
    locator.setInputFiles(file);
  }

  @Override
  @ControllerAction("Download file")
  public Download download(Locator locator, Path target) {
    Download value = page().waitForDownload(locator::click);
    value.saveAs(target);
    return value;
  }

  @Override
  @ControllerAction("Open popup")
  public Page waitForPopup(Runnable trigger) {
    Page popup = page().waitForPopup(trigger);
    installEvidenceListeners(popup);
    page = popup;
    return popup;
  }

  @Override
  public List<Page> pages() {
    return List.copyOf(browserContext().pages());
  }

  @Override
  @ControllerAction("Register dialog handler")
  public void onDialog(Consumer<Dialog> handler) {
    page().onDialog(handler);
  }

  @Override
  @ControllerAction("Register network route")
  public void route(String urlPattern, Consumer<Route> handler) {
    page().route(urlPattern, handler);
  }

  @Override
  public List<Request> requests() {
    return List.copyOf(requests);
  }

  @Override
  @ControllerAction("Save authentication state")
  public void saveStorageState(Path target) {
    browserContext().storageState(new BrowserContext.StorageStateOptions().setPath(target));
  }

  @Override
  @ControllerAction("Capture screenshot")
  public byte[] screenshot() {
    requireVisualPolicy("screenshot");
    return page().screenshot();
  }

  @Override
  @ControllerAction("Capture screenshot")
  public void screenshot(Path target) {
    requireVisualPolicy("screenshot");
    page().screenshot(new Page.ScreenshotOptions().setPath(target));
  }

  @Override
  @ControllerAction("Wait for element state")
  public void waitUntil(Locator locator, WaitForSelectorState target) {
    locator.waitFor(new Locator.WaitForOptions().setState(target));
  }

  @Override
  @ControllerAction("Wait for page condition")
  public void waitUntil(Predicate<Page> predicate) {
    page().waitForCondition(() -> predicate.test(page));
  }

  @Override
  @ControllerAction("Verify element is visible")
  public void expectVisible(Locator locator) {
    PlaywrightAssertions.assertThat(locator).isVisible();
  }

  @Override
  @ControllerAction("Verify element text")
  public void expectText(Locator locator, String text) {
    PlaywrightAssertions.assertThat(locator).hasText(text);
  }
}
