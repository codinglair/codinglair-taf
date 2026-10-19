package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
