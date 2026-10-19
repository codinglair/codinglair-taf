package com.codinglair.taf.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class JacksonNeutralPublicApiTest {
  private static final Map<String, Map<String, Integer>> SER_133_SIGNATURE_REMAINDER =
      Map.of(
          "codinglair-taf-runtime/taf-data-migration/src/main/java/com/codinglair/taf/migration/MongoContextMigration.java",
          Map.of("ObjectMapper", 1),
          "codinglair-taf-runtime/taf-test-definitions-mongodb/src/main/java/com/codinglair/taf/runtime/definition/mongodb/MongoDefinitionSynchronizer.java",
          Map.of("ObjectMapper", 1),
          "codinglair-taf-runtime/taf-test-definitions-mongodb/src/main/java/com/codinglair/taf/runtime/definition/mongodb/MongoTestDefinitionRepository.java",
          Map.of("ObjectMapper", 1));
  private static final Pattern JACKSON_RUNTIME_IMPORT =
      Pattern.compile(
          "import\\s+(?:com\\.fasterxml|tools)\\.jackson\\.(?!annotation\\.)[^;]+\\.([A-Za-z0-9_$]+);");
  private static final Pattern EXPORTED_DECLARATION =
      Pattern.compile("(?s)\\b(?:public|protected)\\b[^;{=]*\\b%s\\b[^;{]*[;{]");

  @Test
  void supportedPublicSourcesExposeNoJacksonRuntimeTypes() throws Exception {
    Path root = repositoryRoot();
    List<String> leaks = new ArrayList<>();
    try (var paths = Files.walk(root)) {
      for (Path source :
          paths
              .filter(path -> path.toString().endsWith(".java"))
              .filter(path -> normalized(path).contains("/src/main/java/"))
              .toList()) {
        inspect(source, leaks);
      }
    }
    assertThat(leaks).as("Jackson runtime types in public/protected TAF contracts").isEmpty();
  }

  @Test
  void commonDoesNotExportJacksonRuntimeOrDataFormats() throws Exception {
    String pom = Files.readString(repositoryRoot().resolve("codinglair-taf-common/pom.xml"));
    assertThat(pom)
        .doesNotContain("<artifactId>jackson-annotations</artifactId>")
        .containsSubsequence("<artifactId>jackson-databind</artifactId>", "<optional>true</optional>")
        .containsSubsequence("<artifactId>jackson-dataformat-csv</artifactId>", "<optional>true</optional>");
  }

  private static void inspect(Path source, List<String> leaks) throws IOException {
    String relative = repositoryRoot().relativize(source).toString().replace('\\', '/');
    Map<String, Integer> allowed =
        new HashMap<>(SER_133_SIGNATURE_REMAINDER.getOrDefault(relative, Map.of()));
    String content = Files.readString(source);
    var imports = JACKSON_RUNTIME_IMPORT.matcher(content);
    while (imports.find()) {
      String type = imports.group(1);
      var declarations =
          Pattern.compile(EXPORTED_DECLARATION.pattern().formatted(Pattern.quote(type)))
              .matcher(content.substring(imports.end()));
      while (declarations.find()) {
        int remaining = allowed.getOrDefault(type, 0);
        if (remaining > 0) {
          allowed.put(type, remaining - 1);
        } else {
          leaks.add(relative + " exposes " + type);
        }
      }
    }
  }

  private static String normalized(Path path) {
    return path.toAbsolutePath().toString().replace('\\', '/');
  }

  private static Path repositoryRoot() {
    Path candidate = Path.of("").toAbsolutePath();
    while (candidate != null && !Files.exists(candidate.resolve("codinglair-taf-common"))) {
      candidate = candidate.getParent();
    }
    if (candidate == null) throw new IllegalStateException("Cannot locate repository root");
    return candidate;
  }
}
