package com.codinglair.taf.qualification.apple;

import com.codinglair.taf.mobile.appium.platform.AppleLocator;
import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.runtime.core.condition.AwaitableAssertion;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

@ActiveProfiles("taf-local")
@ContextConfiguration(
    classes = AppleSimulatorConfiguration.class,
    initializers = AppleSimulatorConfiguration.Initializer.class)
public final class AppleSimulatorSmokeTest extends TafBaseTest {
  private static final AppleLocator NATIVE_ACTION =
      new AppleLocator(AppleLocator.Kind.ACCESSIBILITY, "native-action");
  private static final AppleLocator NATIVE_RESULT =
      new AppleLocator(AppleLocator.Kind.ACCESSIBILITY, "native-result");
  private static final AppleLocator WEB_ACTION =
      new AppleLocator(AppleLocator.Kind.CSS, "#web-action");
  private static final AppleLocator WEB_RESULT =
      new AppleLocator(AppleLocator.Kind.CSS, "#web-result");

  @Test
  public void nativeApplicationInteractionAndRelaunch() throws IOException {
    AppleController apple = controller(AppleController.class, "native");
    apple.tap(apple.find(NATIVE_ACTION));
    assertText(apple, NATIVE_RESULT, "Native changed");
    apple.terminate();
    apple.activate();
    assertText(apple, NATIVE_RESULT, "Ready");
    retainRequiredEvidence(apple, "native");
  }

  @Test
  public void hybridWebViewAndNativeReturn() throws IOException {
    AppleController apple = controller(AppleController.class, "hybrid");
    String webView = awaitWebView(apple);
    apple.selectWebView(webView);
    try {
      apple.tap(apple.find(WEB_ACTION));
      assertText(apple, WEB_RESULT, "Web changed");
    } finally {
      apple.returnToNative();
    }
    assertText(apple, NATIVE_RESULT, "Ready");
    retainRequiredEvidence(apple, "hybrid");
  }

  @Test
  public void mobileSafariUsesLocalDeterministicPage() throws IOException {
    AppleController apple = controller(AppleController.class, "safari");
    apple.navigate(required("APPLE_TEST_URL"));
    apple.tap(apple.find(WEB_ACTION));
    assertText(apple, WEB_RESULT, "Web changed");
    retainRequiredEvidence(apple, "safari");
  }

  @Test
  public void controlledFailureStillUsesNormalSessionCleanup() throws IOException {
    if (!Boolean.getBoolean("taf.apple.controlledFailure")) return;
    AppleController apple = controller(AppleController.class, "native");
    apple.find(NATIVE_ACTION);
    Path ownedSessionFile = Path.of(required("APPLE_OWNED_SESSION_FILE"));
    Files.createDirectories(ownedSessionFile.getParent());
    Files.writeString(
        ownedSessionFile,
        apple.nativeDriver().getSessionId().toString(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
    throw new AssertionError("VER-130-002 controlled failure");
  }

  private String awaitWebView(AppleController apple) {
    var observed = new AtomicReference<Set<String>>(Set.of());
    var matches = new AtomicReference<List<String>>(List.of());
    var result =
        AwaitableAssertion.create(
                "fixture-webview",
                _ -> {
                  Set<String> contexts = apple.contexts();
                  observed.set(contexts);
                  List<String> webViews =
                      contexts.stream()
                          .filter(name -> name.startsWith("WEBVIEW_"))
                          .sorted()
                          .toList();
                  matches.set(webViews);
                  if (webViews.size() > 1)
                    throw new IllegalStateException(
                        "Expected one fixture WebView but found %d".formatted(webViews.size()));
                  return webViews.size() == 1;
                },
                Duration.ofSeconds(30),
                Duration.ofMillis(250))
            .await();
    if (result.isSuccessful()) return matches.get().getFirst();
    if (Thread.currentThread().isInterrupted())
      throw new IllegalStateException("WebView discovery interrupted", result.getErrors().getFirst());
    throw new AssertionError(
        "Fixture WebView discovery failed: %s; context count=%d"
            .formatted(result.getMessage(), observed.get().size()));
  }

  private static void assertText(AppleController apple, AppleLocator locator, String expected) {
    String actual = apple.text(apple.find(locator));
    if (!expected.equals(actual))
      throw new AssertionError("Expected '%s' but observed '%s'".formatted(expected, actual));
  }

  private void retainRequiredEvidence(AppleController apple, String caseName) throws IOException {
    try (var ignored = apple.collectArtifacts(ArtifactReason.EXPLICIT)) {
      // The controller publishes to the session ArtifactCollector before returning.
    }
    var artifacts = testSession().getArtifactCollector().getArtifacts();
    boolean screenshot = artifacts.stream().anyMatch(a -> a.type().equals("screenshot") && !a.content().isBlank());
    boolean source = artifacts.stream().anyMatch(a -> a.type().equals("page-source") && !a.content().isBlank());
    if (!screenshot || !source) throw new AssertionError("Nonempty screenshot and page source are required");
    Path directory = Path.of("target", "apple-evidence", caseName);
    Files.createDirectories(directory);
    for (var artifact : artifacts) {
      String extension = artifact.type().equals("screenshot") ? ".base64" : ".txt";
      Files.writeString(
          directory.resolve(safe(artifact.name()) + extension),
          artifact.content(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW);
    }
  }

  private static String safe(String value) {
    return value.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
    return value;
  }
}
