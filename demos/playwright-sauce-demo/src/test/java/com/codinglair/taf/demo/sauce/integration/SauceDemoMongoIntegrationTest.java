package com.codinglair.taf.demo.sauce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.demo.sauce.model.LoginInput;
import com.codinglair.taf.demo.sauce.model.ProductExpectation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "taf.migration.containers", matches = "true")
@DisplayName("GATE-004C Sauce Demo Mongo golden consumer")
class SauceDemoMongoIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  @DisplayName("materializes committed definitions through Flyway before ordinary repository reads")
  @TestCaseId("TC0901")
  void testNgConsumerDefinitionsComeFromMongoWithNoFileFallback() throws Exception {
    try (MongoDefinitionFixture fixture = new MongoDefinitionFixture()) {
      com.codinglair.taf.runtime.definition.TestDefinitionResolver definitions = fixture.start();
      assertThat(definitions.requireInput("TC0001", LoginInput.class).username())
          .isEqualTo("standard_user");
      assertThat(
              definitions.requireExpectedOutput("TC0002", ProductExpectation.class).productName())
          .isEqualTo("Sauce Labs Backpack");
      assertThat(fixture.definitions()).hasSize(3);
      assertThat(fixture.successfulHistoryEntries("taf_context_history")).isGreaterThanOrEqualTo(3);
      assertThat(fixture.migrationEvidence())
          .isNotEmpty()
          .allSatisfy(
              evidence ->
                  assertThat(evidence.toString())
                      .contains("taf-context")
                      .doesNotContain("mongodb://", "secret://", "passwordReference"));
      assertMaterializedAssets(fixture.definitions());
    }
  }

  @Test
  @DisplayName("blocks execution when an applied consumer migration checksum drifts")
  void checksumDriftBlocksPreflight() throws Exception {
    try (MongoDefinitionFixture fixture = new MongoDefinitionFixture()) {
      fixture.start();
      Path migrationDirectory = Files.createDirectory(temporaryDirectory.resolve("migrations"));
      Path changed = migrationDirectory.resolve("V3__sauce_demo_definitions.json");
      Files.copy(
          Path.of(
              "src/test/resources/db/migration/mongodb/sauce-demo/V3__sauce_demo_definitions.json"),
          changed);
      Files.writeString(
          changed,
          Files.readString(changed).replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2"));

      assertThatThrownBy(() -> fixture.validate(migrationDirectory))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Mongo context migration failed; destroy the scoped context before retry")
          .hasMessageNotContaining("mongodb://")
          .hasMessageNotContaining("passwordReference")
          .hasMessageNotContaining("secret://");
    }
  }

  private static void assertMaterializedAssets(List<org.bson.Document> documents) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var rowsType =
        mapper
            .getTypeFactory()
            .constructCollectionType(
                List.class,
                mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    List<Map<String, Object>> inputs =
        mapper.readValue(
            Files.readString(Path.of("src/test/resources/test-data/inputs.json")), rowsType);
    List<Map<String, Object>> expected =
        mapper.readValue(
            Files.readString(Path.of("src/test/resources/test-data/expected-outputs.json")),
            rowsType);
    assertThat(documents)
        .extracting(document -> document.getString("caseId"))
        .containsExactlyInAnyOrderElementsOf(
            inputs.stream().map(row -> row.get("caseId").toString()).toList());
    assertThat(inputs).hasSameSizeAs(expected).hasSameSizeAs(documents);
    documents.forEach(
        document -> {
          Map<String, Object> sourceInput = row(inputs, document.getString("caseId"));
          Map<String, Object> sourceExpected = row(expected, document.getString("caseId"));
          assertThat(document.get("input", Document.class))
              .containsAllEntriesOf(withoutCaseId(sourceInput));
          assertThat(document.get("expectedOutput", Document.class))
              .containsAllEntriesOf(withoutCaseId(sourceExpected));
          assertThat(document.get("correlations", Document.class).getString("traceability"))
              .isEqualTo(document.getString("caseId"));
          assertThat(
                  document.get("secretReferences", Document.class).getString("passwordReference"))
              .startsWith("secret://");
        });
  }

  private static Map<String, Object> row(List<Map<String, Object>> rows, String caseId) {
    return rows.stream().filter(row -> caseId.equals(row.get("caseId"))).findFirst().orElseThrow();
  }

  private static Map<String, Object> withoutCaseId(Map<String, Object> row) {
    return row.entrySet().stream()
        .filter(entry -> !entry.getKey().equals("caseId"))
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
