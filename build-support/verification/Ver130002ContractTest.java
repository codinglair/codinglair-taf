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
    reject(workflow, "continue-on-error", "pull_request_target");

    String runner = read("qualification/apple-simulator/run-smoke.sh");
    require(runner, "simctl create", "simctl bootstatus", "simctl delete", "trap cleanup",
        "clean deploy -Prelease-staging", "-parse-as-library", "driver install", "/status", "/sessions",
        "controlledFailureStillUsesNormalSessionCleanup", "candidate-artifact-sha256.txt",
        "command -v python3", "cleanup.txt", "compatibility-manifest.txt");
    reject(runner, "latest", "--relaxed-security");

    String smoke = read("qualification/apple-simulator/src/test/java/com/codinglair/taf/qualification/apple/AppleSimulatorSmokeTest.java");
    require(smoke, "controller(AppleController.class", "nativeApplicationInteractionAndRelaunch",
        "hybridWebViewAndNativeReturn", "mobileSafariUsesLocalDeterministicPage",
        "AwaitableAssertion.create", "collectArtifacts(ArtifactReason.EXPLICIT)",
        "Nonempty screenshot and page source are required");
    reject(smoke, "IOSDriver", "Thread.sleep(", "SkipException");

    String pom = read("qualification/apple-simulator/pom.xml");
    require(pom, "<artifactId>codinglair-taf-bom</artifactId>",
        "<artifactId>codinglair-taf-starter-mobile</artifactId>", "${taf.candidate.repository}",
        "<name>taf.apple.live</name>", "<skipTests>true</skipTests>");
    reject(pom, "<parent>");

    String fixture = read("qualification/apple-simulator/fixture/AppDelegate.swift");
    require(fixture, "accessibilityIdentifier = \"native-action\"", "WKWebView",
        "id=\"web-action\"", "id=\"web-result\"");

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
}
