import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Dependency-free DOC-130-001 public-guide, compatibility, and link contract. */
public final class AppleDocumentationTest {
  private static final Path GUIDE =
      Path.of("docs", "reference", "apple-appium-consumer-guide.md");
  private static final Path RECORD =
      Path.of("docs", "reference", "apple-appium-compatibility-1.3.0.md");
  private static final Path ARCHITECTURE =
      Path.of("docs", "architecture", "solution-architecture.md");
  private static final Path CAPABILITY_MATRIX =
      Path.of("docs", "quick-start-capability-matrix.md");
  private static final Path BLUEPRINT_CONTRACT =
      Path.of("docs", "reference", "blueprint-composition-contract.md");
  private static final Path RELEASE_OPERATIONS =
      Path.of("docs", "operations", "nightly-and-release-verification.md");
  private static final Pattern LOCAL_LINK =
      Pattern.compile("\\[[^]]+](\\((?!https?://|#)([^)#]+)(?:#[^)]+)?\\))");

  private AppleDocumentationTest() {}

  public static void main(String[] args) throws IOException {
    var guide = Files.readString(GUIDE);
    var record = Files.readString(RECORD);
    var architecture = Files.readString(ARCHITECTURE);
    var capabilityMatrix = Files.readString(CAPABILITY_MATRIX);
    var blueprintContract = Files.readString(BLUEPRINT_CONTRACT);
    var releaseOperations = Files.readString(RELEASE_OPERATIONS);
    var failures = new ArrayList<String>();

    requireAll(
        guide,
        failures,
        "IPHONE",
        "IPAD",
        "NATIVE",
        "HYBRID",
        "SAFARI",
        "SIMULATOR",
        "PHYSICAL",
        "LOCAL_HOST",
        "REMOTE_HOST",
        "PROVIDER",
        "secret://",
        "SERVER_PATH",
        "WDA",
        "UNKNOWN",
        "STDIO",
        "Streamable HTTP",
        "No named cloud provider is certified");
    requireAll(
        record,
        failures,
        "Appium server | 3.0.0",
        "XCUITest driver | 10.0.0",
        "Node | 22.12.0",
        "npm | 10.9.0",
        "Appium Java client | 10.1.1",
        "Selenium BOM | 4.43.0",
        "VER-130-002",
        "NOT RUN",
        "UNVERIFIED",
        "No remote/provider certification");
    requireAll(
        record,
        failures,
        "Hosted-observed",
        "Candidate 2 passed native, hybrid, Safari",
        "Physical-device, iPad",
        "UNVERIFIED");
    requireAll(
        architecture,
        failures,
        "Apple/XCUITest",
        "ArtifactCollector",
        "mobile.apple",
        "partially supersedes ADR-013",
        "Generic Appium-compatible endpoint support");
    requireAll(
        capabilityMatrix,
        failures,
        "Android/UiAutomator2 and Apple/XCUITest",
        "hosted native/hybrid/Safari simulator qualification passed");
    requireAll(
        blueprintContract,
        failures,
        "family:IPAD}` is a supported Apple",
        "historical `SCF_UNSUPPORTED_IOS` diagnostic applies only to 1.2.0 clients");
    rejectAll(
        blueprintContract,
        failures,
        "family:IPAD}` fails with `SCF_UNSUPPORTED_SELECTION`",
        "explicit unsupported-iOS failure");
    requireAll(
        releaseOperations,
        failures,
        "VER-130-002 Apple Simulator Qualification",
        "pending hosted CI is not an implementation or documentation-review prerequisite");

    checkSource("examples/apple-appium-consumer/pom.xml", "codinglair-taf-starter-mobile", failures);
    for (var file :
        List.of("mobile.yml", "mobile-packaged.yml", "mobile-hybrid.yml", "mobile-safari.yml")) {
      checkSource(
          "examples/apple-appium-consumer/src/test/resources/capabilities/" + file,
          "taf:",
          failures);
    }
    for (var document : List.of(GUIDE, RECORD, ARCHITECTURE)) checkLinks(document, failures);
    if (!failures.isEmpty()) throw new AssertionError(String.join(System.lineSeparator(), failures));
    System.out.println("DOC-130-001 documentation contract passed");
  }

  private static void requireAll(String text, List<String> failures, String... required) {
    for (var value : required) {
      if (!text.contains(value)) failures.add("missing required documentation text: " + value);
    }
  }

  private static void rejectAll(String text, List<String> failures, String... staleValues) {
    for (var value : staleValues) {
      if (text.contains(value)) failures.add("stale documentation text remains: " + value);
    }
  }

  private static void checkSource(String name, String required, List<String> failures)
      throws IOException {
    var path = Path.of(name);
    if (!Files.exists(path) || !Files.readString(path).contains(required)) {
      failures.add("missing or stale executable source: " + name);
    }
  }

  private static void checkLinks(Path document, List<String> failures) throws IOException {
    var matcher = LOCAL_LINK.matcher(Files.readString(document));
    while (matcher.find()) {
      var target = document.getParent().resolve(matcher.group(2)).normalize();
      if (!Files.exists(target)) {
        failures.add("broken local link in " + document + ": " + matcher.group(2));
      }
    }
  }
}
