import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free structural gate for the macOS-only qualification capability. */
public final class Ver130002ContractTest {
  private static final Path ROOT = Path.of("").toAbsolutePath();

  public static void main(String[] args) throws Exception {
    String workflow = read(".github/workflows/ver-130-002-apple-simulator.yml");
    require(workflow, "workflow_dispatch:", "pull_request:", "runs-on: macos-15",
        "java-version: '25'", "node-version: '22.12.0'", "APPIUM_VERSION: '3.0.0'",
        "XCUITEST_VERSION: '10.0.0'", "permissions:\n  contents: read", "if: ${{ always() }}");
    require(workflow, "actions/upload-artifact@v4", "name: apple-simulator-qualification-evidence",
        "target/ver-130-002/**", "!target/ver-130-002/appium-home/**",
        "!target/ver-130-002/wda-derived-data/**",
        "qualification/apple-simulator/target/surefire-reports/**", "Install and verify ffmpeg",
        "brew install ffmpeg", "command -v ffmpeg", "ffmpeg -version");
    requireOrder(workflow, "Install and verify ffmpeg", "Execute real Apple simulator smoke");
    requireOrder(workflow, "Execute real Apple simulator smoke", "Record hosted runner metadata");
    reject(workflow, "continue-on-error", "pull_request_target");

    String runner = read("qualification/apple-simulator/run-smoke.sh");
    require(runner, "simctl create", "simctl bootstatus", "simctl delete", "trap cleanup",
        "clean deploy -Prelease-staging", "-parse-as-library", "driver install", "/status",
        "controlledFailureStillUsesNormalSessionCleanup", "candidate-artifact-sha256.txt",
        "command -v python3", "mktemp", "cleanup.txt", "compatibility-manifest.txt",
        "capture_diagnostics", "sanitize_text_file", "appium-sanitized.log",
        "appium-status-final.json", "simctl-list-final.json",
        "tail -n 250", "cut -c1-2000", "local status=$?", "exit \"$status\"",
        "xcodebuild build-for-testing", "xcodebuild test-without-building",
        "wda-prebuild.log", "wda-launch.log", "wda-status.json", "APPLE_WDA_BASE_URL",
        "record_wda_checkpoint launched", "record_wda_checkpoint ready",
        "record_wda_checkpoint before-appium", "record_wda_checkpoint before-maven",
        "wda-lifecycle.log", "event=xcodebuild-exit", "exitCode=%s", "lsof -nP -iTCP:8100",
        "terminationCause=%s", "WDA_CLEANUP_MARKER", "monitor_wda", "sleep 5",
        "simulator-lifecycle.log", "simulator-bootstatus.log", "elapsedSeconds=%s",
        "record_simulator_checkpoint after-wda-readiness",
        "record_simulator_checkpoint before-appium", "record_simulator_checkpoint before-maven",
        "simulator-runtime-monitor.log", "monitor_simulator", "log stream --style compact",
        "simulator-coresimulator-session.log", "record_simulator_checkpoint cleanup-before-teardown",
        "event=maven-smoke-start", "SIMULATOR_APP=\"$XCODE_PATH/Applications/Simulator.app\"",
        "open -Fn \"$SIMULATOR_APP\"", "simulator-ui-readiness.log", "for _ in {1..30}",
        "Simulator UI failed to become available within 30 seconds",
        "CoreSimulator device failed to remain booted while starting Simulator UI",
        "record_simulator_checkpoint after-simulator-ui-readiness", "APPLE_OWNED_SESSION_FILE",
        "owned-session-cleanup-response.json", "/session/$owned_session/source",
        "invalid session id", "ownedSessionCleanupVerified=true", "appium-hybrid-context.log",
        "remote.?debug", "tail -n 500");
    requireOrder(runner, "candidate_log=\"$(mktemp", "clean deploy -Prelease-staging");
    requireOrder(runner, "clean deploy -Prelease-staging", "xcodebuild -version");
    requireOrder(runner, "capture_diagnostics", "kill \"$appium_pid\"");
    reject(runner, "latest", "--relaxed-security", "> \"$OUT/logs/appium.log\"", "/sessions");
    requireOrder(runner, "record_wda_checkpoint before-appium", "appium_raw_log=\"$(mktemp");
    requireOrder(runner, "record_wda_checkpoint before-maven", "-Dtaf.apple.live=true test");
    requireOrder(runner, "event=before-simctl-boot", "xcrun simctl boot \"$udid\"");
    requireOrder(runner, "xcrun simctl bootstatus", "open -Fn \"$SIMULATOR_APP\"");
    requireOrder(runner, "open -Fn \"$SIMULATOR_APP\"", "xcrun simctl install");
    requireOrder(runner, "record_simulator_checkpoint before-appium", "appium_raw_log=\"$(mktemp");
    requireOrder(runner, "record_simulator_checkpoint before-maven", "-Dtaf.apple.live=true test");

    String smoke = read("qualification/apple-simulator/src/test/java/com/codinglair/taf/qualification/apple/AppleSimulatorSmokeTest.java");
    require(smoke, "controller(AppleController.class", "nativeApplicationInteractionAndRelaunch",
        "hybridWebViewAndNativeReturn", "mobileSafariUsesLocalDeterministicPage",
        "@ActiveProfiles(\"taf-local\")", "AwaitableAssertion.create",
        "AppleSimulatorConfiguration.Initializer.class",
        "collectArtifacts(ArtifactReason.EXPLICIT)",
        "Nonempty screenshot and page source are required", "APPLE_OWNED_SESSION_FILE",
        "apple.nativeDriver().getSessionId().toString()", "StandardOpenOption.TRUNCATE_EXISTING",
        "WEB_LOAD_STATUS", "fixture-webview-navigation", "context-observations.txt",
        "observations.merge(contexts, 1, Integer::sum)");
    reject(smoke, "IOSDriver", "Thread.sleep(", "SkipException");

    String configurationTest = read("qualification/apple-simulator/src/test/java/com/codinglair/taf/qualification/apple/AppleSimulatorConfigurationTest.java");
    require(configurationTest, "AppleSimulatorConfiguration.Initializer.class",
        "hasController(AppleController.class, \"native\")",
        "hasController(AppleController.class, \"hybrid\")",
        "hasController(AppleController.class, \"safari\")");
    String configuration = read("qualification/apple-simulator/src/test/java/com/codinglair/taf/qualification/apple/AppleSimulatorConfiguration.java");
    require(configuration, "YamlPropertySourceLoader", "ClassPathResource(\"application.yml\")",
        "Cannot load Apple simulator configuration");
    String appleProperties = read("qualification/apple-simulator/src/test/resources/application.yml");
    require(appleProperties, "command-timeout: 5m", "build-mode: RUNNING",
        "base-url: '${APPLE_WDA_BASE_URL}'", "show-xcode-log: true",
        "additional-webview-bundle-ids:", "process-TafAppleFixture");
    require(appleProperties, "launch-timeout: 3m");

    String appleStrategy = read("codinglair-taf-runtime/taf-mobile-appium/src/main/java/com/codinglair/taf/mobile/appium/platform/ApplePlatformStrategy.java");
    require(appleStrategy, "appium:additionalWebviewBundleIds",
        "settings.getAdditionalWebviewBundleIds()");

    String pom = read("qualification/apple-simulator/pom.xml");
    require(pom, "<artifactId>codinglair-taf-bom</artifactId>",
        "<artifactId>codinglair-taf-starter-mobile</artifactId>", "${taf.candidate.repository}",
        "<name>taf.apple.live</name>", "<skipTests>true</skipTests>");
    reject(pom, "<parent>");

    String fixture = read("qualification/apple-simulator/fixture/AppDelegate.swift");
    require(fixture, "accessibilityIdentifier = \"native-action\"", "WKWebView",
        "id=\"web-action\"", "id=\"web-result\"", "webView.isInspectable = true",
        "WKNavigationDelegate", "accessibilityIdentifier = \"web-load-status\"",
        "webLoadStatus.text = \"Web loaded\"");

    String record = read("docs/qualification/apple/VER-130-002-candidate-1.md");
    require(record, "**Hosted Apple simulator qualification:** **NOT RUN / UNVERIFIED**",
        "**Implementation:** COMPLETE", "**Physical-device qualification:** **UNVERIFIED**",
        "Appium 3.0.0", "XCUITest 10.0.0", "Java client 10.1.1", "Selenium 4.43.0");
    System.out.println("VER-130-002 structural contract passed");
  }

  private static String read(String relative) throws Exception {
    Path path = ROOT.resolve(relative);
    if (!Files.isRegularFile(path)) throw new AssertionError("Missing required file: " + relative);
    return Files.readString(path);
  }

  private static void require(String text, String... values) {
    for (String value : values)
      if (!text.contains(value)) throw new AssertionError("Missing required contract text: " + value);
  }

  private static void reject(String text, String... values) {
    for (String value : values)
      if (text.contains(value)) throw new AssertionError("Forbidden contract text: " + value);
  }

  private static void requireOrder(String text, String first, String second) {
    int firstIndex = text.indexOf(first);
    int secondIndex = text.indexOf(second);
    if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex)
      throw new AssertionError("Required contract order is missing: " + first + " before " + second);
  }
}
