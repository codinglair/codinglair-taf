package com.codinglair.taf.runtime.core.reporting.impl.allure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllureCliSingleFileReportGeneratorIntegrationTest {
  @TempDir Path temporary;

  @Test
  void realAllureCliProducesOneOfflineArtifactAndPreservesSanitization() throws Exception {
    String executable = approvedExecutable();
    Assumptions.assumeTrue(executable != null, "approved Allure CLI path is not configured");
    Path results = temporary.resolve("allure-results");
    Files.createDirectories(results);
    String uuid = UUID.randomUUID().toString();
    Files.writeString(
        results.resolve(uuid + "-result.json"),
        "{\"uuid\":\""
            + uuid
            + "\",\"historyId\":\"rep-005\",\"name\":\"Sanitized result [REDACTED]\","
            + "\"fullName\":\"rep005.Sanitized\",\"status\":\"passed\",\"stage\":\"finished\","
            + "\"start\":1,\"stop\":2,\"labels\":[{\"name\":\"suite\",\"value\":\"REP-005\"}]}",
        StandardCharsets.UTF_8);

    AllureSingleFileProperties properties = new AllureSingleFileProperties();
    properties.setEnabled(true);
    properties.setResultsDirectory(results);
    properties.setOutputDirectory(temporary.resolve("published"));
    properties.setReportName("Real Generator Report");
    properties.setExecutable(executable);
    properties.setTimeout(Duration.ofMinutes(2));
    AllureSingleFilePublisher publisher =
        new AllureSingleFilePublisher(
            properties,
            new AllureCliSingleFileReportGenerator(),
            Clock.fixed(Instant.parse("2026-08-10T02:45:00Z"), ZoneOffset.UTC));

    Path artifact = publisher.publishOnce().orElseThrow();

    assertThat(artifact).isRegularFile().isNotEmptyFile();
    assertThat(artifact.getFileName()).hasToString("Real Generator Report_202608100245.html");
    try (var siblings = Files.list(artifact.getParent())) {
      assertThat(siblings.map(Path::getFileName)).containsExactly(artifact.getFileName());
    }
    String html = Files.readString(artifact, StandardCharsets.UTF_8);
    assertThat(html.toLowerCase(Locale.ROOT)).contains("<html").contains("<script");
    assertThat(html).doesNotContain("allure-secret-canary");
    assertThat(results.resolve(uuid + "-result.json")).exists();
  }

  private static String approvedExecutable() {
    String configured = System.getProperty("taf.test.allure.executable");
    if (configured == null || configured.isBlank()) {
      configured = System.getenv("TAF_TEST_ALLURE_EXECUTABLE");
    }
    return configured == null || configured.isBlank() ? null : configured;
  }
}
