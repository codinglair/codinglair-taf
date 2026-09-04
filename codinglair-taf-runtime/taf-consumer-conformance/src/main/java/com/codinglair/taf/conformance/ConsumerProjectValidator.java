package com.codinglair.taf.conformance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Validates an unpacked Maven consumer project against blueprint 1.0. Thread safe. */
public final class ConsumerProjectValidator {
  public static final String BLUEPRINT_VERSION = "1.0";
  private static final Pattern PACKAGE = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");
  private static final Pattern PROJECT = Pattern.compile("[a-z][a-z0-9-]{0,99}");
  private static final Set<String> CAPABILITIES =
      Set.of(
          "web-playwright",
          "rest",
          "soap",
          "kafka",
          "rabbitmq",
          "jms",
          "database",
          "files",
          "mobile-appium");
  private static final Set<String> RUNNERS = Set.of("testng", "cucumber-testng");
  private static final Map<String, String> REQUIRED_ARTIFACT =
      Map.of(
          "web-playwright",
          "taf-web-playwright",
          "testng",
          "codinglair-taf-runner-testng",
          "cucumber-testng",
          "codinglair-taf-runner-cucumber",
          "file-csv",
          "taf-test-definitions");
  private static final Map<String, List<String>> PROHIBITED_WHEN_UNSELECTED =
      Map.of(
          "web-playwright", List.of("taf-web-playwright", "playwright"),
          "cucumber-testng",
              List.of("codinglair-taf-runner-cucumber", "cucumber-java", "cucumber-testng"));
  private static final ObjectMapper JSON =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public ConformanceReport validate(Path projectRoot) {
    Path root = normalizeRoot(projectRoot);
    List<ConformanceViolation> out = new ArrayList<>();
    BlueprintDescriptor descriptor = descriptor(root, out);
    if (descriptor == null) return new ConformanceReport(BLUEPRINT_VERSION, out);
    validateDescriptor(root, descriptor, out);
    validatePom(root, descriptor, out);
    validateSpringResources(root, out);
    validateSources(root, descriptor, out);
    validateRequiredAssets(root, out);
    return new ConformanceReport(BLUEPRINT_VERSION, out);
  }

  public ConformanceReport validateReportOutput(Path reportFile) {
    List<ConformanceViolation> out = new ArrayList<>();
    Path file = reportFile.toAbsolutePath().normalize();
    try {
      JsonNode root = JSON.readTree(file.toFile());
      Set<String> levels = new HashSet<>();
      Set<String> ids = new HashSet<>();
      JsonNode events = root.path("events");
      if (!events.isArray())
        violation(
            out,
            "report.hierarchy",
            file,
            "Report output has no events array",
            "Export neutral hierarchical report events");
      else
        events.forEach(
            event -> {
              String id = event.path("id").asText();
              String level = event.path("level").asText();
              if (id.isBlank() || !ids.add(id))
                violation(
                    out,
                    "report.duplicate",
                    file,
                    "Report event IDs must be present and unique",
                    "Emit each logical event exactly once");
              levels.add(level);
            });
      if (!levels.contains("TEST") || !levels.contains("VALIDATION"))
        violation(
            out,
            "report.hierarchy",
            file,
            "Report lacks TEST or VALIDATION hierarchy levels",
            "Preserve test-to-workflow/action-to-validation hierarchy");
    } catch (IOException failure) {
      violation(
          out,
          "report.readable",
          file,
          "Report output is missing or invalid JSON",
          "Supply the sanitized neutral report inspection output");
    }
    return new ConformanceReport(BLUEPRINT_VERSION, out);
  }

  private static Path normalizeRoot(Path root) {
    if (root == null) throw new IllegalArgumentException("projectRoot must not be null");
    Path normalized = root.toAbsolutePath().normalize();
    if (!Files.isDirectory(normalized))
      throw new IllegalArgumentException("projectRoot must be an existing directory");
    return normalized;
  }

  private static BlueprintDescriptor descriptor(Path root, List<ConformanceViolation> out) {
    Path file = root.resolve("src/test/resources/taf-project.json");
    try {
      return JSON.readValue(file.toFile(), BlueprintDescriptor.class);
    } catch (IOException failure) {
      violation(
          out,
          "descriptor.schema",
          file,
          "Descriptor is missing, malformed, or contains unknown fields",
          "Provide a strict blueprint 1.0 descriptor at src/test/resources/taf-project.json");
      return null;
    }
  }

  private static void validateDescriptor(
      Path root, BlueprintDescriptor d, List<ConformanceViolation> out) {
    Path file = root.resolve("src/test/resources/taf-project.json");
    if (!BLUEPRINT_VERSION.equals(d.schemaVersion()))
      violation(
          out,
          "descriptor.version",
          file,
          "Unsupported blueprint version",
          "Use schemaVersion 1.0 or an approved migrated schema");
    if (d.project() == null
        || !PROJECT.matcher(orEmpty(d.project().name())).matches()
        || !PACKAGE.matcher(orEmpty(d.project().basePackage())).matches()
        || !Set.of("focused-golden", "cross-capability", "consumer").contains(d.project().type()))
      violation(
          out,
          "descriptor.project",
          file,
          "Project metadata does not satisfy blueprint 1.0",
          "Use a valid project name, type, and dotted lowercase base package");
    if (d.runtime() == null || orEmpty(d.runtime().version()).isBlank())
      violation(
          out,
          "descriptor.runtime",
          file,
          "Runtime version is required",
          "Declare a pinned or Maven-property Runtime version");
    uniqueSelection(d.capabilities(), CAPABILITIES, "capabilities", file, out);
    uniqueSelection(d.runners(), RUNNERS, "runners", file, out);
    if (d.testDefinitions() == null
        || !d.testDefinitions().authoritative()
        || !Set.of("file-csv", "mongodb", "custom").contains(d.testDefinitions().provider()))
      violation(
          out,
          "descriptor.test-definitions",
          file,
          "Exactly one authoritative provider is required",
          "Select file-csv, mongodb, or custom with authoritative true");
    if (d.reporting() == null || !Set.of("allure", "custom").contains(d.reporting().adapter()))
      violation(
          out,
          "descriptor.reporting",
          file,
          "Reporting adapter is invalid",
          "Select allure or custom");
  }

  private static void uniqueSelection(
      List<String> values,
      Set<String> allowed,
      String name,
      Path file,
      List<ConformanceViolation> out) {
    if (values == null
        || values.isEmpty()
        || new HashSet<>(values).size() != values.size()
        || !allowed.containsAll(values))
      violation(
          out,
          "descriptor." + name,
          file,
          "Selection is empty, duplicated, or unsupported",
          "Choose unique blueprint 1.0 " + name);
  }

  private static void validatePom(
      Path root, BlueprintDescriptor d, List<ConformanceViolation> out) {
    Path pom = root.resolve("pom.xml");
    String text = read(pom, out, "dependency.pom");
    Set<String> selected = new HashSet<>(safe(d.capabilities()));
    selected.addAll(safe(d.runners()));
    if (d.testDefinitions() != null) selected.add(d.testDefinitions().provider());
    selected.forEach(
        item -> {
          String artifact = REQUIRED_ARTIFACT.get(item);
          if (artifact != null && !text.contains("<artifactId>" + artifact + "</artifactId>"))
            violation(
                out,
                "dependency.selected",
                pom,
                "Selected " + item + " module is absent",
                "Declare " + artifact + " in the consumer build");
        });
    PROHIBITED_WHEN_UNSELECTED.forEach(
        (selection, artifacts) -> {
          if (!selected.contains(selection))
            artifacts.forEach(
                artifact -> {
                  if (text.contains("<artifactId>" + artifact + "</artifactId>"))
                    violation(
                        out,
                        "dependency.unselected",
                        pom,
                        "Unselected capability dependency is present: " + artifact,
                        "Remove the unselected starter/vendor dependency");
                });
        });
    for (String forbidden : List.of("codinglair-taf-mcp", "codinglair-taf-quality-intelligence"))
      if (text.contains("<artifactId>" + forbidden + "</artifactId>"))
        violation(
            out,
            "dependency.prohibited",
            pom,
            "Consumer depends on prohibited product module: " + forbidden,
            "Depend only on selected Runtime capabilities");
  }

  private static void validateSpringResources(Path root, List<ConformanceViolation> out) {
    Path resources = root.resolve("src/test/resources");
    for (String name : List.of("application.yaml", "application-local.yaml", "application-ci.yaml"))
      if (!Files.isRegularFile(resources.resolve(name)))
        violation(
            out,
            "spring.profile",
            resources.resolve(name),
            "Required Spring profile resource is absent",
            "Add base, local, and CI typed configuration resources");
    if (Files.isDirectory(resources))
      try (Stream<Path> files = Files.walk(resources)) {
        files
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().startsWith("application"))
            .forEach(
                file -> {
                  String text = read(file, out, "configuration.readable");
                  if (Pattern.compile(
                          "(?i)(https?://(?!\\$\\{|REPLACE_ME)|password\\s*:\\s*(?!\\$\\{|REPLACE_ME)|token\\s*:\\s*(?!\\$\\{|REPLACE_ME)|secret\\s*:\\s*(?!\\$\\{|REPLACE_ME))")
                      .matcher(text)
                      .find())
                    violation(
                        out,
                        "configuration.externalized",
                        file,
                        "Hard-coded environment or sensitive value detected",
                        "Use an unresolved placeholder or opaque secret-reference alias");
                });
      } catch (IOException failure) {
        violation(
            out,
            "configuration.readable",
            resources,
            "Cannot inspect configuration resources",
            "Make project resources readable");
      }
    validatePropertyAuthority(resources, out);
  }

  private static void validateSources(
      Path root, BlueprintDescriptor d, List<ConformanceViolation> out) {
    List<Path> main = javaFiles(root.resolve("src/main/java"));
    List<Path> tests = javaFiles(root.resolve("src/test/java"));
    boolean boot =
        main.stream()
            .anyMatch(p -> read(p, out, "source.readable").contains("@SpringBootApplication"));
    if (!boot)
      violation(
          out,
          "spring.bootstrap",
          root.resolve("src/main/java"),
          "No Spring Boot application was found",
          "Add one conventional non-web @SpringBootApplication bootstrap class");
    for (Path file : concat(main, tests)) {
      String text = read(file, out, "source.readable");
      JavaSourceRules.inspect(file, text, tests.contains(file))
          .forEach(
              finding ->
                  violation(
                      out,
                      finding.rule(),
                      file,
                      finding.detail() + " at line " + finding.line(),
                      correctionFor(finding.rule())));
      if (Pattern.compile(
              "(?i)(https?://[^\\s\"']+|(?:password|token|secret)\\s*=\\s*\"(?!\\$\\{|REPLACE_ME)[^\"]+\")")
          .matcher(text)
          .find())
        violation(
            out,
            "configuration.externalized",
            file,
            "Hard-coded environment or sensitive value detected",
            "Use typed external configuration or an opaque secret-reference alias");
      if ((text.contains("implements ITestListener")
              || text.contains("extends TestListenerAdapter"))
          && (text.contains("TestSession")
              || text.contains("openSession")
              || text.contains("closeSession")))
        violation(
            out,
            "lifecycle.listener-owned",
            file,
            "A listener appears to own session lifecycle",
            "Restrict listeners to observation/reporting");
    }
    tests.forEach(
        file -> {
          String text = read(file, out, "source.readable");
          if (text.contains("@Test")
              && !text.contains("@TestCaseId")
              && !text.contains("AbstractTestNGCucumberTests"))
            violation(
                out,
                "traceability.testng",
                file,
                "TestNG test has no @TestCaseId",
                "Declare one unique test-case identifier per executable test");
        });
    validateUniqueJavaTraceability(tests, out);
    validateResolvedJavaTraceability(root.resolve("src/test/resources/test-data"), tests, out);
    validateFeatureTraceability(root.resolve("src/test/resources/features"), out);
    if (safe(d.capabilities()).contains("web-playwright")) {
      requireDirectory(root.resolve("src/main/java"), "page", "architecture.page", out);
    }
  }

  private static void validateRequiredAssets(Path root, List<ConformanceViolation> out) {
    requireMatching(
        root.resolve("src/main/java"),
        "@ConfigurationProperties",
        "spring.typed-configuration",
        out);
    requireMatching(root.resolve("src/test/java"), "@SpringBootTest", "spring.context-test", out);
    requireExtension(root.resolve("src/test/resources"), ".xml", "spring.suite", out);
    Path data = root.resolve("src/test/resources/test-data");
    if (!Files.isDirectory(data))
      violation(
          out,
          "test-data.required",
          data,
          "Test data resources are absent",
          "Add authoritative test data resources");
  }

  private static void requireMatching(
      Path root, String token, String rule, List<ConformanceViolation> out) {
    boolean found =
        javaFiles(root).stream()
            .anyMatch(file -> read(file, out, "source.readable").contains(token));
    if (!found)
      violation(
          out,
          rule,
          root,
          "Required consumer asset is absent: " + token,
          "Add the blueprint-required asset");
  }

  private static void requireExtension(
      Path root, String extension, String rule, List<ConformanceViolation> out) {
    boolean found = false;
    if (Files.isDirectory(root))
      try (Stream<Path> paths = Files.walk(root)) {
        found =
            paths.anyMatch(
                path -> Files.isRegularFile(path) && path.toString().endsWith(extension));
      } catch (IOException ignored) {
        // The missing/readability violation below remains actionable.
      }
    if (!found)
      violation(
          out,
          rule,
          root,
          "Required TestNG suite resource is absent",
          "Add a selected-runner TestNG XML suite");
  }

  private static void validatePropertyAuthority(Path resources, List<ConformanceViolation> out) {
    Path base = resources.resolve("application.yaml");
    if (!Files.isRegularFile(base)) return;
    Set<String> baseKeys = yamlKeys(read(base, out, "configuration.readable"));
    duplicateYamlKeys(base, out);
    String previousProfile = null;
    for (String profile : List.of("application-local.yaml", "application-ci.yaml")) {
      Path file = resources.resolve(profile);
      if (!Files.isRegularFile(file)) continue;
      String profileText = read(file, out, "configuration.readable");
      duplicateYamlKeys(file, out);
      if (previousProfile != null && previousProfile.equals(profileText.strip()))
        violation(
            out,
            "configuration.profile-duplicate",
            file,
            "Local and CI profiles are identical",
            "Keep only intentional environment-specific overrides in each profile");
      previousProfile = profileText.strip();
      for (String key : yamlKeys(profileText))
        if (!baseKeys.contains(key))
          violation(
              out,
              "configuration.property-authority",
              file,
              "Profile declares property without authoritative base declaration: " + key,
              "Declare the property in application.yaml and override it intentionally in profiles");
    }
  }

  private static void duplicateYamlKeys(Path file, List<ConformanceViolation> out) {
    Set<String> keys = new HashSet<>();
    for (String key : yamlKeyList(read(file, out, "configuration.readable")))
      if (!keys.add(key))
        violation(
            out,
            "configuration.property-duplicate",
            file,
            "Property is declared more than once in one resource: " + key,
            "Keep one authoritative declaration per property in each resource");
  }

  private static Set<String> yamlKeys(String text) {
    return new HashSet<>(yamlKeyList(text));
  }

  private static List<String> yamlKeyList(String text) {
    List<String> keys = new ArrayList<>();
    List<String> parents = new ArrayList<>();
    for (String line : text.lines().toList()) {
      String trimmed = line.strip();
      int colon = trimmed.indexOf(':');
      if (!trimmed.isEmpty() && !trimmed.startsWith("#") && colon > 0) {
        int indent = line.length() - line.stripLeading().length();
        int depth = indent / 2;
        while (parents.size() > depth) parents.removeLast();
        String key = trimmed.substring(0, colon).strip();
        List<String> path = new ArrayList<>(parents);
        path.add(key);
        keys.add(String.join(".", path));
        if (trimmed.substring(colon + 1).strip().isEmpty()) {
          while (parents.size() < depth) parents.add("");
          if (parents.size() == depth) parents.add(key);
        }
      }
    }
    return keys;
  }

  private static String correctionFor(String rule) {
    if (rule.startsWith("locator."))
      return "Declare immutable LocatorSpec fields in their POM/PCOM/screen owner and reference them from actions";
    if (rule.startsWith("lifecycle."))
      return "Use framework-owned TafBaseTest or TafCucumberHooks lifecycle wiring";
    if (rule.startsWith("reporting.")) return "Use TAF reporter-neutral annotations and contracts";
    if (rule.startsWith("state."))
      return "Resolve invocation-bound objects from the active TestSession and keep them out of static state";
    return "Use injected collaborators and the approved consumer dependency direction";
  }

  private static void validateUniqueJavaTraceability(
      List<Path> tests, List<ConformanceViolation> out) {
    Pattern idPattern = Pattern.compile("@TestCaseId\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    Set<String> ids = new HashSet<>();
    tests.forEach(
        file -> {
          var matcher = idPattern.matcher(read(file, out, "source.readable"));
          while (matcher.find())
            if (!ids.add(matcher.group(1)))
              violation(
                  out,
                  "traceability.duplicate",
                  file,
                  "Duplicate test-case identifier: " + matcher.group(1),
                  "Assign one unique identifier to each executable test");
        });
  }

  private static void validateResolvedJavaTraceability(
      Path data, List<Path> tests, List<ConformanceViolation> out) {
    Pattern idPattern = Pattern.compile("@TestCaseId\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    tests.forEach(
        file -> {
          var matcher = idPattern.matcher(read(file, out, "source.readable"));
          while (matcher.find()) {
            String id = matcher.group(1);
            if (!Files.isRegularFile(data.resolve(id + ".json"))
                && !Files.isRegularFile(data.resolve(id + ".yaml"))
                && !Files.isRegularFile(data.resolve(id + ".csv")))
              violation(
                  out,
                  "traceability.unresolved",
                  file,
                  "Test-case identifier has no authoritative test-data resource: " + id,
                  "Add a matching " + id + " test-data resource or correct the identifier");
          }
        });
  }

  private static void validateFeatureTraceability(Path features, List<ConformanceViolation> out) {
    if (!Files.isDirectory(features)) return;
    Set<String> ids = new HashSet<>();
    try (Stream<Path> files = Files.walk(features)) {
      files
          .filter(p -> p.toString().endsWith(".feature"))
          .forEach(
              file -> {
                String text = read(file, out, "source.readable");
                if (text.contains("Scenario:") && !text.contains("@test-case-"))
                  violation(
                      out,
                      "traceability.cucumber",
                      file,
                      "Cucumber scenario has no test-case tag",
                      "Add a unique @test-case-<id> tag");
                var matcher = Pattern.compile("@test-case-([A-Za-z0-9._-]+)").matcher(text);
                while (matcher.find()) {
                  String id = matcher.group(1);
                  if (!ids.add(id))
                    violation(
                        out,
                        "traceability.duplicate",
                        file,
                        "Duplicate Cucumber test-case identifier: " + id,
                        "Assign one unique identifier to each executable scenario");
                  Path data = features.getParent().resolve("test-data");
                  if (!Files.isRegularFile(data.resolve(id + ".json"))
                      && !Files.isRegularFile(data.resolve(id + ".yaml"))
                      && !Files.isRegularFile(data.resolve(id + ".csv")))
                    violation(
                        out,
                        "traceability.unresolved",
                        file,
                        "Cucumber test-case identifier has no authoritative resource: " + id,
                        "Add a matching test-data resource or correct the tag");
                }
              });
    } catch (IOException failure) {
      violation(
          out,
          "source.readable",
          features,
          "Cannot inspect feature resources",
          "Make feature resources readable");
    }
  }

  private static void requireDirectory(
      Path source, String name, String rule, List<ConformanceViolation> out) {
    if (!hasDirectory(source, name))
      violation(
          out,
          rule,
          source,
          "Selected capability lacks the " + name + " responsibility layer",
          "Add a distinct " + name + " package");
  }

  private static boolean hasDirectory(Path source, String name) {
    if (!Files.isDirectory(source)) return false;
    try (Stream<Path> paths = Files.walk(source)) {
      return paths.anyMatch(p -> Files.isDirectory(p) && p.getFileName().toString().equals(name));
    } catch (IOException ignored) {
      return false;
    }
  }

  private static List<Path> javaFiles(Path root) {
    if (!Files.isDirectory(root)) return List.of();
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(p -> p.toString().endsWith(".java")).toList();
    } catch (IOException ignored) {
      return List.of();
    }
  }

  private static List<Path> concat(List<Path> a, List<Path> b) {
    List<Path> all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }

  private static List<String> safe(List<String> value) {
    return value == null ? List.of() : value;
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String read(Path file, List<ConformanceViolation> out, String rule) {
    try {
      return Files.readString(file);
    } catch (IOException failure) {
      violation(
          out,
          rule,
          file,
          "Required project file is missing or unreadable",
          "Provide a readable project file");
      return "";
    }
  }

  private static void violation(
      List<ConformanceViolation> out, String rule, Path file, String message, String correction) {
    out.add(new ConformanceViolation(rule, file, message, correction));
  }
}
