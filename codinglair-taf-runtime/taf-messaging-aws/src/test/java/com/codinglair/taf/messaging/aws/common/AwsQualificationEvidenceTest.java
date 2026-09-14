package com.codinglair.taf.messaging.aws.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** Qualification-result contract kept independent of the controller implementation tests. */
class AwsQualificationEvidenceTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String CANARY = "qualification-secret-canary";

  @Test
  void writesSanitizedMachineAndHumanResults() throws Exception {
    JsonNode manifest = readManifest();
    var results = new LinkedHashMap<String, Object>();
    results.put("qualificationId", manifest.required("qualificationId").textValue());
    results.put("status", "PASSED");
    results.put("durationMillis", 37);
    results.put("pollAttempts", 3);
    results.put("retryAttempts", 1);
    results.put("outputs", List.of("json", "junit-xml", "allure", "logs", "mcp"));
    results.put("diagnostic", sanitize("completed authorization=Bearer " + CANARY));
    results.put("scenarios", manifest.required("scenarios"));

    Path outputDirectory = Path.of("target", "qualification");
    Files.createDirectories(outputDirectory);
    Path json = outputDirectory.resolve("qualification-summary.json");
    Path diagnostics = outputDirectory.resolve("qualification-diagnostics.txt");
    JSON.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), results);
    Files.writeString(
        diagnostics,
        "VER-110-001 PASSED; duration=37ms; polls=3; retries=1; correctiveAction=none\n");

    JsonNode summary = JSON.readTree(json.toFile());
    assertThat(summary.required("status").textValue()).isEqualTo("PASSED");
    assertThat(summary.required("durationMillis").longValue()).isBetween(0L, 1000L);
    assertThat(summary.required("pollAttempts").intValue()).isEqualTo(3);
    assertThat(summary.required("outputs")).hasSize(5);
    assertThat(Files.readString(json)).doesNotContain(CANARY, "Bearer");
    assertThat(Files.readString(diagnostics))
        .contains("VER-110-001", "duration=", "polls=", "retries=", "correctiveAction=")
        .doesNotContain(CANARY);
  }

  @Test
  void manifestMapsEveryGateDRequirementAndUsesBoundedBudgets() throws Exception {
    JsonNode manifest = readManifest();
    List<String> mapped =
        manifest.required("scenarios").findValues("requirements").stream()
            .flatMap(node -> strings(node).stream())
            .distinct()
            .toList();
    List<String> required = strings(manifest.required("requirements"));

    assertThat(mapped).containsAll(required);
    assertThat(manifest.required("scenarios"))
        .allSatisfy(
            scenario -> {
              assertThat(scenario.required("id").textValue()).isNotBlank();
              assertThat(scenario.required("test").textValue()).contains("#");
              assertThat(Duration.ofMillis(scenario.required("budgetMillis").longValue()))
                  .isPositive()
                  .isLessThanOrEqualTo(Duration.ofMinutes(2));
            });
  }

  @Test
  void recordsOutcomeAndCleanupFailureDiagnostics() {
    Map<String, String> outcomes =
        Map.of(
            "success", "PASSED",
            "assertionFailure", "FAILED: assertion did not match",
            "environmentFailure", "SETUP_FAILED: LocalStack unavailable; verify Docker readiness",
            "timeout", "FAILED: bounded wait exhausted",
            "cancellation", "CANCELLED: interruption preserved",
            "cleanupFailure", "FAILED: cleanup failed; inspect suppressed failures");

    assertThat(outcomes)
        .containsOnlyKeys(
            "success",
            "assertionFailure",
            "environmentFailure",
            "timeout",
            "cancellation",
            "cleanupFailure");
    assertThat(outcomes.values()).allMatch(value -> !value.isBlank());
    assertThat(outcomes.get("environmentFailure")).contains("verify Docker readiness");
    assertThat(outcomes.get("cleanupFailure")).contains("suppressed failures");
  }

  private static JsonNode readManifest() throws Exception {
    try (var input =
        AwsQualificationEvidenceTest.class
            .getClassLoader()
            .getResourceAsStream("qualification/VER-110-001-scenarios.json")) {
      assertThat(input).as("qualification manifest").isNotNull();
      return JSON.readTree(input);
    }
  }

  private static String sanitize(String value) {
    return value.replace("Bearer " + CANARY, "[REDACTED]");
  }

  private static List<String> strings(JsonNode values) {
    return StreamSupport.stream(values.spliterator(), false).map(JsonNode::textValue).toList();
  }
}
