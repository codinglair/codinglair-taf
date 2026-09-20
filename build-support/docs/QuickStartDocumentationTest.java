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
  private static final Path DEPENDENCIES = Path.of("docs", "reference", "consumer-dependencies.md");
  private static final Path MCP_DEPLOYMENT = Path.of("docs", "operations", "mcp-container-deployment.md");
  private static final Path ARCHITECTURE = Path.of("docs", "architecture", "solution-architecture.md");
  private static final Path SNIPPET_MANIFEST =
      Path.of("docs", "reference", "consumer-snippet-manifest-v1.json");
  private static final Path STARTER_MANIFEST =
      Path.of("docs", "reference", "starter-capability-manifest-v1.json");
  private static final Path BOM = Path.of("codinglair-taf-bom", "pom.xml");
  private static final Pattern LOCAL_LINK = Pattern.compile("\\[[^]]+](\\((?!https?://|#)([^)#]+)(?:#[^)]+)?\\))");

  private QuickStartDocumentationTest() {}

  public static void main(String[] args) throws IOException {
    String text = Files.readString(DOCUMENT);
    String matrix = Files.readString(MATRIX);
    String dependencies = Files.readString(DEPENDENCIES);
    String deployment = Files.readString(MCP_DEPLOYMENT);
    String architecture = Files.readString(ARCHITECTURE);
    String snippetManifest = Files.readString(SNIPPET_MANIFEST);
    String starterManifest = Files.readString(STARTER_MANIFEST);
    String allDocumentation = String.join(System.lineSeparator(), text, matrix, dependencies,
        deployment, architecture);
    List<String> failures = new ArrayList<>();
    for (String required : List.of(
        "Java 25", "TestNG", "Cucumber", "Playwright", "REST", "DatabaseController",
        "EventBridge", "SQS", "STDIO", "Streamable HTTP", "secret://", "revision", "F2B",
        "MCP")) {
      if (!allDocumentation.contains(required)) failures.add("missing required topic: " + required);
    }
    for (String heading : List.of(
        "## 2. Prerequisites and SUT information", "## 3. Create a clean consumer project",
        "## 6. TestNG and Cucumber", "### 6.1 TestNG for technical verification",
        "### 6.2 Cucumber for curated business behavior",
        "### 7.1 Browser UI with Playwright", "### 7.2 REST API",
        "### 7.4 Database validation", "### 7.8 EventBridge and SQS",
        "## 8. Complete front-to-back pattern",
        "## 12. Troubleshooting user projects")) {
      if (!text.contains(heading)) failures.add("missing required section: " + heading);
    }
    for (String forbidden : List.of(
        "session.getPlaywrightController()", "session.getRestController()",
        "session.getCucumberController()", "@CucumberContext", "assert !",
        "DB_PASSWORD:change_me", "supersecret123", "org.webtools.browser.BrowserInstall")) {
      if (text.contains(forbidden)) failures.add("stale or unsafe example: " + forbidden);
    }
    for (String starter : List.of(
        "codinglair-taf-starter-web", "codinglair-taf-starter-api",
        "codinglair-taf-starter-database", "codinglair-taf-starter-messaging",
        "codinglair-taf-starter-mobile", "codinglair-taf-starter-messaging-kafka",
        "codinglair-taf-starter-messaging-rabbitmq", "codinglair-taf-starter-messaging-jms",
        "codinglair-taf-starter-messaging-aws")) {
      if (!starterManifest.contains("\"starter\": \"" + starter + "\"")) {
        failures.add("starter missing from authoritative manifest: " + starter);
      }
      if (!text.contains("`" + starter + "`") && !dependencies.contains("`" + starter + "`")) {
        failures.add("starter missing from consumer guidance: " + starter);
      }
    }
    for (String required : List.of(
        "\"targetRelease\": \"1.2.0\"", "docs/quick-start.md",
        "docs/reference/consumer-dependencies.md", "docs/operations/mcp-container-deployment.md",
        "docs/architecture/solution-architecture.md")) {
      if (!snippetManifest.contains(required)) failures.add("snippet manifest missing: " + required);
    }
    for (String required : List.of("```xml", "```groovy", "```kotlin", "@pom",
        "blueprint/project generation")) {
      if (!dependencies.contains(required)) failures.add("dependency guide missing: " + required);
    }
    for (String required : List.of("codinglair/codinglair-taf-mcp:1.2.0", "docker run --rm -i",
        "streamable-http", "/actuator/health/liveness", "/actuator/health/readiness",
        "Kubernetes supports Streamable HTTP only")) {
      if (!deployment.contains(required)) failures.add("MCP deployment guide missing: " + required);
    }
    if (deployment.contains("codinglair/codinglair-taf-mcp:latest")
        || deployment.contains("latest` is reproducible")) {
      failures.add("MCP deployment guide makes unsafe mutable-tag usage claim");
    }
    for (String required : List.of("EventBridge", "SQS", "LocalStack", "starter",
        "supported direct", "codinglair/codinglair-taf-mcp", "STDIO", "Streamable HTTP",
        "Docker", "Kubernetes")) {
      if (!architecture.contains(required)) failures.add("public architecture missing: " + required);
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
    for (Path document : List.of(DOCUMENT, MATRIX, DEPENDENCIES, MCP_DEPLOYMENT, ARCHITECTURE)) {
      var matcher = LOCAL_LINK.matcher(Files.readString(document));
      while (matcher.find()) {
        Path target = document.getParent().resolve(matcher.group(2)).normalize();
        if (!Files.exists(target)) failures.add("broken local link in " + document + ": " + matcher.group(2));
      }
    }
    if (!failures.isEmpty()) throw new AssertionError(String.join(System.lineSeparator(), failures));
    System.out.println("DOC-120-001 documentation contract passed: snippets, versions, consistency, and links");
  }
}
