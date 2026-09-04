import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Dependency-free DOC-001 link, coverage, and fence contract. */
public final class QuickStartDocumentationTest {
  private static final Path DOCUMENT = Path.of("docs", "quick-start.md");
  private static final Path MATRIX = Path.of("docs", "quick-start-capability-matrix.md");
  private static final Path BOM = Path.of("codinglair-taf-bom", "pom.xml");
  private static final Pattern LOCAL_LINK = Pattern.compile("\\[[^]]+](\\((?!https?://|#)([^)#]+)(?:#[^)]+)?\\))");

  private QuickStartDocumentationTest() {}

  public static void main(String[] args) throws IOException {
    String text = Files.readString(DOCUMENT);
    String matrix = Files.readString(MATRIX);
    String allDocumentation = text + System.lineSeparator() + matrix;
    List<String> failures = new ArrayList<>();
    for (String required : List.of(
        "Java 25", "TestNG", "Cucumber", "Playwright", "REST", "DatabaseController",
        "STDIO", "Streamable HTTP", "secret://", "revision", "F2B", "MCP")) {
      if (!allDocumentation.contains(required)) failures.add("missing required topic: " + required);
    }
    for (String heading : List.of(
        "## 2. Prerequisites and SUT information", "## 3. Create a clean consumer project",
        "## 6. TestNG and Cucumber", "### 6.1 TestNG for technical verification",
        "### 6.2 Cucumber for curated business behavior",
        "### 7.1 Browser UI with Playwright", "### 7.2 REST API",
        "### 7.4 Database validation", "## 8. Complete front-to-back pattern",
        "## 12. Troubleshooting user projects")) {
      if (!text.contains(heading)) failures.add("missing required section: " + heading);
    }
    for (String forbidden : List.of(
        "session.getPlaywrightController()", "session.getRestController()",
        "session.getCucumberController()", "@CucumberContext", "assert !",
        "DB_PASSWORD:change_me", "supersecret123", "org.webtools.browser.BrowserInstall")) {
      if (text.contains(forbidden)) failures.add("stale or unsafe example: " + forbidden);
    }
    for (Path source : List.of(
        Path.of("demos", "playwright-sauce-demo", "pom.xml"),
        Path.of("docs", "examples", "quick-start-pom.xml"),
        Path.of("demos", "playwright-sauce-demo", "src", "test", "java", "com", "codinglair",
            "taf", "demo", "sauce", "quickstart", "RestApiExample.java"),
        Path.of("demos", "playwright-sauce-demo", "src", "test", "java", "com", "codinglair",
            "taf", "demo", "sauce", "quickstart", "DatabaseValidationExample.java"),
        Path.of("demos", "playwright-sauce-demo", "src", "test", "java", "com", "codinglair",
            "taf", "demo", "sauce", "quickstart", "FrontToBackExample.java"),
        Path.of("docs", "examples", "src", "test", "java", "com", "example", "automation",
            "PresentationOnlyCapabilityExamples.java"))) {
      if (!Files.exists(source)) failures.add("missing compiled golden-consumer source: " + source);
    }
    var artifactMatcher =
        Pattern.compile("<artifactId>([^<]+)</artifactId>").matcher(Files.readString(BOM));
    while (artifactMatcher.find()) {
      String artifact = artifactMatcher.group(1);
      if (!List.of("codinglair-taf", "codinglair-taf-bom").contains(artifact)
          && !matrix.contains("`" + artifact + "`")) {
        failures.add("BOM artifact missing from capability matrix: " + artifact);
      }
    }
    long fences = allDocumentation.lines().filter(line -> line.startsWith("```")).count();
    if (fences % 2 != 0) failures.add("unbalanced Markdown code fences: " + fences);
    var matcher = LOCAL_LINK.matcher(allDocumentation);
    while (matcher.find()) {
      Path target = DOCUMENT.getParent().resolve(matcher.group(2)).normalize();
      if (!Files.exists(target)) failures.add("broken local link: " + matcher.group(2));
    }
    if (!failures.isEmpty()) throw new AssertionError(String.join(System.lineSeparator(), failures));
    System.out.println("DOC-001 quick-start contract passed: topics, fences, and local links");
  }
}
