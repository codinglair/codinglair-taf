package com.codinglair.taf.runtime.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** DOC-002 contract for links and source-derived public reference coverage. */
class DocumentationReferenceTest {
  private static final Path ROOT = Path.of("..", "..").normalize();
  private static final Path REFERENCE = ROOT.resolve("docs/reference");
  private static final Pattern LOCAL_LINK =
      Pattern.compile("\\[[^]]+]\\((?!https?://|#)([^)#]+)(?:#[^)]+)?\\)");
  private static final Pattern CONFIGURATION_PREFIX =
      Pattern.compile("@ConfigurationProperties\\((?:prefix\\s*=\\s*)?\"([^\"]+)\"\\)");

  @Test
  void referenceCoversPublishedArtifactsConfigurationSchemasAndAudiences() throws IOException {
    List<String> failures = new ArrayList<>();
    String reference = readMarkdown(REFERENCE);

    for (String required :
        List.of(
            "TAF user",
            "MCP client or operator",
            "Platform operator",
            "Extension author",
            "Maintainer",
            "TestSession",
            "Java 25",
            "Spring Boot 4",
            "minor release",
            "./mvnw -Pdocs verify")) {
      if (!reference.contains(required))
        failures.add("missing required reference topic: " + required);
    }

    for (String requiredDocument :
        List.of(
            "installation-and-prerequisites.md",
            "android-appium-setup.md",
            "testng-cucumber-separation.md",
            "training-session-guide.md")) {
      if (!Files.isRegularFile(REFERENCE.resolve(requiredDocument))) {
        failures.add("missing public reference document: " + requiredDocument);
      }
      if (!reference.contains(requiredDocument)) {
        failures.add("public reference document is not linked: " + requiredDocument);
      }
    }

    for (String requiredEnablementTopic :
        List.of(
            "CI/CD examples",
            "Consolidated configuration example",
            "Persistent job model",
            "Secret and redaction examples",
            "Executable demonstrations and training",
            "TestNG and Cucumber usage and separation",
            "Android and Appium setup and troubleshooting")) {
      if (!reference.contains(requiredEnablementTopic)) {
        failures.add("missing public enablement topic: " + requiredEnablementTopic);
      }
    }

    var artifactMatcher =
        Pattern.compile("<artifactId>([^<]+)</artifactId>")
            .matcher(Files.readString(ROOT.resolve("codinglair-taf-bom/pom.xml")));
    while (artifactMatcher.find()) {
      String artifact = artifactMatcher.group(1);
      if (!List.of("codinglair-taf", "codinglair-taf-bom").contains(artifact)
          && !reference.contains("`" + artifact + "`")) {
        failures.add("released BOM artifact has no reference entry: " + artifact);
      }
    }

    try (Stream<Path> sources = Files.walk(ROOT)) {
      sources
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> path.toString().contains("src\\main\\java"))
          .forEach(path -> collectConfigurationPrefix(path, reference, failures));
    }

    try (Stream<Path> resources = Files.walk(ROOT)) {
      resources
          .filter(path -> path.getFileName().toString().endsWith("schema.json"))
          .filter(path -> !path.toString().contains("target"))
          .filter(path -> !path.toString().contains("src\\test"))
          .forEach(
              path -> {
                if (!reference.contains(path.getFileName().toString())) {
                  failures.add(
                      "authoritative schema has no reference entry: " + ROOT.relativize(path));
                }
              });
    }

    try (Stream<Path> documents = Files.list(REFERENCE)) {
      documents
          .filter(path -> path.toString().endsWith(".md"))
          .forEach(path -> validateDocument(path, failures));
    }

    assertThat(failures).as(String.join(System.lineSeparator(), failures)).isEmpty();
  }

  private static String readMarkdown(Path directory) throws IOException {
    StringBuilder result = new StringBuilder();
    try (Stream<Path> documents = Files.list(directory)) {
      for (Path path :
          documents.filter(item -> item.toString().endsWith(".md")).sorted().toList()) {
        result.append(Files.readString(path)).append(System.lineSeparator());
      }
    }
    return result.toString();
  }

  private static void collectConfigurationPrefix(
      Path path, String reference, List<String> failures) {
    try {
      var matcher = CONFIGURATION_PREFIX.matcher(Files.readString(path));
      while (matcher.find()) {
        String prefix = matcher.group(1);
        if (!reference.contains("`" + prefix + "`")) {
          failures.add("@ConfigurationProperties prefix has no reference entry: " + prefix);
        }
      }
    } catch (IOException exception) {
      failures.add("cannot read source " + path + ": " + exception.getMessage());
    }
  }

  private static void validateDocument(Path document, List<String> failures) {
    try {
      String text = Files.readString(document);
      long fences = text.lines().filter(line -> line.startsWith("```")).count();
      if (fences % 2 != 0) failures.add("unbalanced Markdown fences: " + document);
      var matcher = LOCAL_LINK.matcher(text);
      while (matcher.find()) {
        Path target = document.getParent().resolve(matcher.group(1)).normalize();
        if (!Files.exists(target))
          failures.add("broken local link in " + document + ": " + matcher.group(1));
      }
    } catch (IOException exception) {
      failures.add("cannot validate " + document + ": " + exception.getMessage());
    }
  }
}
