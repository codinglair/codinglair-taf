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
    String attachment = "rep-005-attachment.txt";
    Files.writeString(
        results.resolve(attachment),
        "sessionId=s1\ntestId=t1\nstepId=step1\nattempt=1\ndiagnostic=ok\ncontentType=text/plain\nsha256=recorded",
        StandardCharsets.UTF_8);
    Files.writeString(
        results.resolve(uuid + "-result.json"),
        "{\"uuid\":\""
            + uuid
            + "\",\"historyId\":\"rep-005\",\"name\":\"Sanitized result [REDACTED]\","
            + "\"fullName\":\"rep005.Sanitized\",\"status\":\"passed\",\"stage\":\"finished\","
            + "\"start\":1,\"stop\":2,\"labels\":[{\"name\":\"suite\",\"value\":\"REP-005\"}],"
            + "\"attachments\":[{\"name\":\"diagnostic\",\"source\":\""
            + attachment
            + "\",\"type\":\"text/plain\"}]}",
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

    Path matrix = temporary.resolve("expectations.json");
    Files.writeString(
        matrix,
        """
        {"expectedResultCount":1,
         "requiredFields":["sessionId","testId","stepId","attempt","diagnostic","contentType","sha256"],
         "forbiddenPatterns":["allure-secret-canary"],
         "expectedResults":[{"name":"Sanitized result [REDACTED]","status":"passed","count":1,
           "attachments":[{"name":"diagnostic","contentType":"text/plain","count":1}]}]}
        """,
        StandardCharsets.UTF_8);
    var validation = new AllureEvidenceValidator().validate(results, artifact, matrix);
    assertThat(validation.violations()).isEmpty();
    preserveAcceptanceEvidence(results, artifact, matrix, validation);
  }

  private static String approvedExecutable() {
    String configured = System.getProperty("taf.test.allure.executable");
    if (configured == null || configured.isBlank()) {
      configured = System.getenv("TAF_TEST_ALLURE_EXECUTABLE");
    }
    return configured == null || configured.isBlank() ? null : configured;
  }

  private static void preserveAcceptanceEvidence(
      Path results, Path report, Path matrix, AllureEvidenceValidator.ValidationReport validation)
      throws Exception {
    Path bundle = Path.of("target", "consumer-evidence");
    if (Files.exists(bundle)) {
      try (var paths = Files.walk(bundle)) {
        for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
    }
    Path retainedResults = bundle.resolve("allure-results");
    Files.createDirectories(retainedResults);
    try (var files = Files.list(results)) {
      for (Path file : files.toList()) {
        Files.copy(
            file,
            retainedResults.resolve(file.getFileName()),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
    Files.copy(
        report,
        bundle.resolve("standalone.html"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    Files.copy(
        matrix,
        bundle.resolve("expectations.json"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(
        bundle.resolve("validator.json"), validation.toJson(), StandardCharsets.UTF_8);
  }
}
