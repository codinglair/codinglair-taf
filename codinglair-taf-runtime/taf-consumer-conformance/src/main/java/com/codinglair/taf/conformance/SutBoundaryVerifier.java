package com.codinglair.taf.conformance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * Inspects an explicit SUT scope for reactor, dependency, source, and packaged TAF contamination.
 */
public final class SutBoundaryVerifier {
  public ConformanceReport verify(Path sutRoot, Path automationRoot) {
    Path sut = existingRoot(sutRoot, "sutRoot");
    Path automation = automationRoot.toAbsolutePath().normalize();
    List<ConformanceViolation> violations = new ArrayList<>();
    try (var paths = Files.walk(sut)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> !path.toAbsolutePath().normalize().startsWith(automation))
          .forEach(path -> inspect(sut, automation, path, violations));
    } catch (IOException failure) {
      throw new IllegalArgumentException("SUT protected scope cannot be inspected", failure);
    }
    return new ConformanceReport(ConsumerProjectValidator.STANDALONE_BLUEPRINT_VERSION, violations);
  }

  private static void inspect(
      Path sut, Path automation, Path file, List<ConformanceViolation> violations) {
    String name = file.getFileName().toString();
    if (name.equals("pom.xml") || name.endsWith(".gradle") || name.endsWith(".gradle.kts")) {
      String build = read(file);
      if (build.contains("com.codinglair.taf") && build.contains("<dependencyManagement>"))
        violation(violations, "sut.bom", file, "SUT build imports the TAF BOM");
      else if (build.contains("com.codinglair.taf"))
        violation(violations, "sut.dependency", file, "SUT build resolves a TAF artifact");
      String relativeAutomation = sut.relativize(automation).toString().replace('\\', '/');
      if (!relativeAutomation.startsWith("..")
          && build.replace('\\', '/').contains(relativeAutomation))
        violation(violations, "sut.reactor", file, "SUT build registers the automation root");
    }
    if (file.toString().contains("src" + java.io.File.separator + "main")
        && name.endsWith(".java")
        && read(file).contains("com.codinglair.taf"))
      violation(violations, "sut.source", file, "SUT production source imports TAF");
    if (name.endsWith(".jar") || name.endsWith(".war")) inspectArchive(file, violations);
  }

  private static void inspectArchive(Path file, List<ConformanceViolation> violations) {
    try (InputStream input = Files.newInputStream(file);
        var archive = new ZipInputStream(input)) {
      for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
        String name = entry.getName();
        if (name.startsWith("com/codinglair/taf/")
            || name.contains("/com/codinglair/taf/")
            || name.contains("codinglair-taf")) {
          violation(violations, "sut.package", file, "Packaged SUT contains TAF classes/artifacts");
          return;
        }
      }
    } catch (IOException failure) {
      violation(violations, "sut.package-readable", file, "SUT archive cannot be inspected");
    }
  }

  private static Path existingRoot(Path root, String name) {
    if (root == null || !Files.isDirectory(root.toAbsolutePath().normalize()))
      throw new IllegalArgumentException(name + " must be an existing directory");
    try {
      return root.toRealPath();
    } catch (IOException failure) {
      throw new IllegalArgumentException(name + " cannot be resolved", failure);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException failure) {
      return "";
    }
  }

  private static void violation(
      List<ConformanceViolation> out, String rule, Path file, String message) {
    out.add(
        new ConformanceViolation(
            rule,
            file,
            message,
            "Remove the automation/TAF edge from the SUT and rebuild its production package"));
  }
}
