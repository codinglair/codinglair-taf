package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Standalone automation SUT boundary integration")
class StandaloneSutBoundaryIntegrationTest {
  @TempDir Path temporary;

  @ParameterizedTest(name = "layout {0}")
  @ValueSource(strings = {"sibling", "nested"})
  @DisplayName("preserves effective reactor, production graph, protected files, and package")
  void preservesSutBoundary(String layout) throws Exception {
    Path repository = Files.createDirectories(temporary.resolve(layout));
    Path sut = createSut(repository.resolve("sut"));
    Path automation =
        layout.equals("nested") ? sut.resolve("test-automation") : repository.resolve("automation");
    Path evidence = Files.createDirectories(repository.resolve("evidence"));

    buildSut(sut, evidence.resolve("before-effective.xml"), evidence.resolve("before-tree.txt"));
    Map<Path, String> beforeProtected = protectedHashes(sut, automation);
    String beforeEffective = hash(evidence.resolve("before-effective.xml"));
    String beforeTree = hash(evidence.resolve("before-tree.txt"));
    String beforeArchive = hash(sut.resolve("target/representative-sut-1.0.0.jar"));

    createAutomationMarker(automation);
    assertThat(new SutBoundaryVerifier().verify(sut, automation).conforms()).isTrue();
    buildSut(sut, evidence.resolve("after-effective.xml"), evidence.resolve("after-tree.txt"));

    assertThat(hash(evidence.resolve("after-effective.xml"))).isEqualTo(beforeEffective);
    assertThat(hash(evidence.resolve("after-tree.txt"))).isEqualTo(beforeTree);
    assertThat(hash(sut.resolve("target/representative-sut-1.0.0.jar"))).isEqualTo(beforeArchive);
    assertThat(protectedHashes(sut, automation)).isEqualTo(beforeProtected);
    assertArchiveIsClean(sut.resolve("target/representative-sut-1.0.0.jar"));

    Path hidden = repository.resolve("automation-removed-from-sut");
    Files.move(automation, hidden);
    buildSut(sut, evidence.resolve("absent-effective.xml"), evidence.resolve("absent-tree.txt"));
    assertThat(hash(evidence.resolve("absent-effective.xml"))).isEqualTo(beforeEffective);
    assertThat(hash(evidence.resolve("absent-tree.txt"))).isEqualTo(beforeTree);
  }

  private static Path createSut(Path root) throws IOException {
    Files.createDirectories(root.resolve("src/main/java/example/sut"));
    Files.writeString(
        root.resolve("pom.xml"),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>example.sut</groupId>
          <artifactId>representative-sut</artifactId>
          <version>1.0.0</version>
          <properties>
            <maven.compiler.release>25</maven.compiler.release>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <project.build.outputTimestamp>2026-01-01T00:00:00Z</project.build.outputTimestamp>
          </properties>
          <build>
            <pluginManagement>
              <plugins>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-compiler-plugin</artifactId>
                  <version>3.13.0</version>
                </plugin>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-resources-plugin</artifactId>
                  <version>3.3.1</version>
                </plugin>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-jar-plugin</artifactId>
                  <version>3.4.2</version>
                </plugin>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-help-plugin</artifactId>
                  <version>3.5.2</version>
                </plugin>
                <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-dependency-plugin</artifactId>
                  <version>3.8.1</version>
                </plugin>
              </plugins>
            </pluginManagement>
          </build>
        </project>
        """);
    Files.writeString(
        root.resolve("src/main/java/example/sut/Application.java"),
        "package example.sut; public final class Application { private Application() {} }\n");
    return root;
  }

  private static void createAutomationMarker(Path root) throws IOException {
    Files.createDirectories(root.resolve("src/test/resources"));
    Files.writeString(
        root.resolve("pom.xml"), "<project><!-- independent automation --></project>");
    Files.writeString(root.resolve("src/test/resources/taf-project.json"), "{}\n");
  }

  private static void buildSut(Path sut, Path effective, Path tree) throws Exception {
    Path wrapper = repositoryRoot().resolve(isWindows() ? "mvnw.cmd" : "mvnw");
    run(wrapper, sut, "-B", "-ntp", "package");
    run(wrapper, sut, "-B", "-ntp", "help:effective-pom", "-Doutput=" + effective);
    run(wrapper, sut, "-B", "-ntp", "dependency:tree", "-DoutputFile=" + tree);
  }

  private static void run(Path wrapper, Path workingDirectory, String... arguments)
      throws Exception {
    var command = new java.util.ArrayList<String>();
    if (isWindows()) {
      command.add(System.getenv().getOrDefault("ComSpec", "C:\\Windows\\System32\\cmd.exe"));
      command.add("/c");
    } else {
      command.add("sh");
    }
    command.add(wrapper.toString());
    command.addAll(java.util.List.of(arguments));
    var process =
        new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor()).as(output).isZero();
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath();
    String wrapper = isWindows() ? "mvnw.cmd" : "mvnw";
    while (current != null && !Files.isRegularFile(current.resolve(wrapper)))
      current = current.getParent();
    if (current == null) throw new IllegalStateException("repository Maven Wrapper is unavailable");
    return current;
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").startsWith("Windows");
  }

  private static Map<Path, String> protectedHashes(Path sut, Path automation) throws IOException {
    var hashes = new TreeMap<Path, String>();
    try (var files = Files.walk(sut)) {
      files
          .filter(Files::isRegularFile)
          .filter(path -> !path.startsWith(automation))
          .filter(path -> !path.startsWith(sut.resolve("target")))
          .forEach(path -> hashes.put(sut.relativize(path), hash(path)));
    }
    return hashes;
  }

  private static void assertArchiveIsClean(Path archive) throws IOException {
    try (var jar = new JarFile(archive.toFile())) {
      assertThat(jar.stream().map(entry -> entry.getName()))
          .noneMatch(name -> name.contains("codinglair") || name.contains("test-automation"));
    }
  }

  private static String hash(Path file) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }
}
