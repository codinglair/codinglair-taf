package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SUT boundary verifier")
class SutBoundaryVerifierTest {
  @TempDir Path temporary;

  @Test
  @DisplayName("accepts an independent same-repository automation build")
  void acceptsIndependentBuild() throws Exception {
    Path sut = Files.createDirectories(temporary.resolve("sut"));
    Files.writeString(sut.resolve("pom.xml"), "<project><artifactId>sut</artifactId></project>");
    Path automation = Files.createDirectories(temporary.resolve("automation"));

    var report = new SutBoundaryVerifier().verify(sut, automation);

    assertThat(report.conforms()).isTrue();
  }

  @Test
  @DisplayName("reports a TAF dependency in the SUT build")
  void rejectsTafDependency() throws Exception {
    Path sut = Files.createDirectories(temporary.resolve("sut"));
    Files.writeString(
        sut.resolve("pom.xml"),
        "<project><dependency><groupId>com.codinglair.taf</groupId></dependency></project>");

    var report = new SutBoundaryVerifier().verify(sut, temporary.resolve("automation"));

    assertThat(report.conforms()).isFalse();
    assertThat(report.violations())
        .extracting(ConformanceViolation::rule)
        .contains("sut.dependency");
  }

  @Test
  @DisplayName("reports TAF BOM import before any prohibited test body can execute")
  void rejectsTafBom() throws Exception {
    Path sut = Files.createDirectories(temporary.resolve("sut"));
    Files.writeString(
        sut.resolve("pom.xml"),
        "<project><dependencyManagement><dependencies><dependency>"
            + "<groupId>com.codinglair.taf</groupId><artifactId>codinglair-taf-bom</artifactId>"
            + "</dependency></dependencies></dependencyManagement></project>");
    Path sentinel = temporary.resolve("test-body-ran.sentinel");

    var report = new SutBoundaryVerifier().verify(sut, temporary.resolve("automation"));

    assertThat(report.violations()).extracting(ConformanceViolation::rule).contains("sut.bom");
    assertThat(sentinel).doesNotExist();
  }

  @Test
  @DisplayName("reports registration of nested automation in the SUT reactor")
  void rejectsAutomationReactorRegistration() throws Exception {
    Path sut = Files.createDirectories(temporary.resolve("sut"));
    Path automation = Files.createDirectories(sut.resolve("automation"));
    Files.writeString(
        sut.resolve("pom.xml"),
        "<project><modules><module>automation</module></modules></project>");

    var report = new SutBoundaryVerifier().verify(sut, automation);

    assertThat(report.violations()).extracting(ConformanceViolation::rule).contains("sut.reactor");
  }

  @Test
  @DisplayName("reports TAF classes packaged in a SUT archive")
  void rejectsPackagedTafClass() throws Exception {
    Path sut = Files.createDirectories(temporary.resolve("sut"));
    Files.writeString(sut.resolve("pom.xml"), "<project/>");
    try (var archive =
        new JarOutputStream(Files.newOutputStream(sut.resolve("contaminated.jar")))) {
      archive.putNextEntry(new JarEntry("BOOT-INF/classes/com/codinglair/taf/Leak.class"));
      archive.write(new byte[] {0});
      archive.closeEntry();
    }

    var report = new SutBoundaryVerifier().verify(sut, temporary.resolve("automation"));

    assertThat(report.violations()).extracting(ConformanceViolation::rule).contains("sut.package");
  }
}
