package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Standalone scaffold filesystem boundary")
class StandaloneScaffoldBoundaryTest {
  @TempDir Path temporary;

  @Nested
  @DisplayName("Unsafe roots")
  class UnsafeRoots {
    @Test
    @DisplayName("rejects traversal outside the approved root")
    void rejectsTraversal() {
      var boundary = new StandaloneScaffoldBoundary(temporary);

      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> boundary.resolve(temporary.resolve("..").resolve("escape"), null));

      assertThat(failure).hasMessageContaining("escapes the approved root");
    }

    @Test
    @DisplayName("rejects the SUT build root")
    void rejectsSutRoot() {
      var boundary = new StandaloneScaffoldBoundary(temporary);

      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> boundary.resolve(temporary.resolve("sut"), temporary.resolve("sut")));

      assertThat(failure).hasMessageContaining("must not be the SUT build root");
    }

    @Test
    @DisplayName("rejects a nonempty unrecognized project")
    void rejectsUnknownProject() throws Exception {
      Path project = Files.createDirectory(temporary.resolve("unknown"));
      Files.writeString(project.resolve("pom.xml"), "<project/>");
      var boundary = new StandaloneScaffoldBoundary(temporary);

      var failure =
          assertThrows(IllegalArgumentException.class, () -> boundary.resolve(project, null));

      assertThat(failure).hasMessageContaining("not a recognized TAF consumer project");
    }

    @Test
    @DisplayName("rejects an unavailable approved ancestor")
    void rejectsMissingApprovedAncestor() {
      Path missing = temporary.resolve("missing");

      var failure =
          assertThrows(
              IllegalArgumentException.class, () -> new StandaloneScaffoldBoundary(missing));

      assertThat(failure).hasMessageContaining("approved root is unavailable");
    }

    @Test
    @DisplayName("rejects a symbolic-link ancestor")
    void rejectsSymbolicLinkAncestor() throws Exception {
      Path outside = Files.createDirectory(temporary.resolve("outside"));
      Path link = temporary.resolve("linked");
      createSymbolicLinkOrSkip(link, outside);
      var boundary = new StandaloneScaffoldBoundary(temporary);

      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> boundary.resolve(link.resolve("automation"), null));

      assertThat(failure).hasMessageContaining("filesystem-link");
    }

    @Test
    @DisplayName("rejects path substitution after validation")
    void rejectsPathSubstitution() throws Exception {
      Path automation = Files.createDirectory(temporary.resolve("automation"));
      Path outside = Files.createDirectory(temporary.resolve("outside"));
      Path directory = Files.createDirectory(automation.resolve("generated"));
      var boundary = new StandaloneScaffoldBoundary(temporary);
      Path target = boundary.target(automation, Path.of("generated", "file.txt"));
      Files.delete(directory);
      createSymbolicLinkOrSkip(directory, outside);

      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> boundary.verifyTargetAtWrite(automation, target));

      assertThat(failure).hasMessageContaining("link");
    }

    @Test
    @DisplayName("rejects a Windows junction ancestor when junction creation is permitted")
    void rejectsWindowsJunctionAncestor() throws Exception {
      assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows junction test");
      Path outside = Files.createDirectory(temporary.resolve("junction-target"));
      Path junction = temporary.resolve("junction");
      Process process =
          new ProcessBuilder(
                  "cmd.exe", "/c", "mklink", "/J", junction.toString(), outside.toString())
              .redirectErrorStream(true)
              .start();
      String output = new String(process.getInputStream().readAllBytes());
      assumeTrue(process.waitFor() == 0, "Windows junction creation unavailable: " + output.trim());
      var boundary = new StandaloneScaffoldBoundary(temporary);

      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> boundary.resolve(junction.resolve("automation"), null));

      assertThat(failure).hasMessageContaining("filesystem-link");
    }
  }

  @Nested
  @DisplayName("Supported roots")
  class SupportedRoots {
    @Test
    @DisplayName("accepts a same-repository automation sibling")
    void acceptsSibling() throws Exception {
      Path sut = Files.createDirectory(temporary.resolve("sut"));
      var boundary = new StandaloneScaffoldBoundary(temporary);

      Path resolved = boundary.resolve(temporary.resolve("automation"), sut);

      assertThat(resolved).isEqualTo(temporary.resolve("automation").toAbsolutePath());
    }
  }

  private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | IOException failure) {
      assumeTrue(false, "symbolic-link creation unavailable: " + failure.getMessage());
    }
  }
}
