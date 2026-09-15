package com.codinglair.taf.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Blueprint composition contract")
class BlueprintCompositionContractTest {
  private static final Path ROOT = repositoryRoot();
  private static final Path BLUEPRINT = ROOT.resolve("blueprints/playwright-consumer-v1");
  private static final Path CONTRACT =
      ROOT.resolve("docs/reference/blueprint-composition-contract.md");
  private static final Path REQUEST_SCHEMA =
      ROOT.resolve("docs/architecture/schemas/scaffold-request-v1.schema.json");
  private static final Path CONTRIBUTION_SCHEMA =
      ROOT.resolve("docs/architecture/schemas/blueprint-contribution-v1.schema.json");
  private static final ObjectMapper JSON = new ObjectMapper();

  @Nested
  @DisplayName("Legacy inventory")
  class LegacyInventory {
    @Test
    @DisplayName("accounts for every existing Playwright blueprint file exactly once")
    void accountsForEveryExistingBlueprintFile() throws IOException {
      String contract = Files.readString(CONTRACT);
      List<String> files;
      try (Stream<Path> paths = Files.walk(BLUEPRINT)) {
        files =
            paths
                .filter(Files::isRegularFile)
                .map(BLUEPRINT::relativize)
                .map(path -> path.toString().replace('\\', '/'))
                .sorted()
                .toList();
      }

      assertThat(files).hasSize(38);
      assertThat(files)
          .allSatisfy(
              file ->
                  assertThat(occurrences(contract, "| `" + file + "` |"))
                      .as("inventory row for %s", file)
                      .isEqualTo(1));
    }

    @Test
    @DisplayName("assigns every allowed classification and a migration disposition")
    void assignsClassificationsAndDispositions() throws IOException {
      String contract = Files.readString(CONTRACT);

      assertThat(contract)
          .contains("| common |")
          .contains("| Web |")
          .contains("| reusable capability |")
          .contains("| runner/reporting |")
          .contains("| provider |")
          .contains("| obsolete |");
    }
  }

  @Nested
  @DisplayName("Versioned schemas")
  class VersionedSchemas {
    @Test
    @DisplayName("defines a closed normalized request with deterministic selection enums")
    void definesClosedNormalizedRequest() throws IOException {
      JsonNode schema = JSON.readTree(REQUEST_SCHEMA.toFile());
      JsonNode example =
          JSON.readTree(ROOT.resolve("docs/reference/scaffold-request-v1.example.json").toFile());

      assertThat(schema.path("$schema").asText()).contains("2020-12");
      assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
      assertThat(
              textValues(schema.path("properties").path("capabilities").path("items").path("enum")))
          .containsExactly("API", "DATABASE", "MESSAGING", "MOBILE", "WEB");
      assertThat(example.path("schemaVersion").asText()).isEqualTo("1.0");
      assertThat(example.path("blueprintVersion").asText()).isEqualTo("1.0");
      assertThat(textValues(example.path("capabilities")))
          .isSortedAccordingTo(Comparator.naturalOrder())
          .contains("MESSAGING", "MOBILE", "WEB");
      assertThat(example.path("mobile").path("platform").asText()).isEqualTo("ANDROID");
      assertThat(example.path("mobile").path("automationName").asText()).isEqualTo("UIAUTOMATOR2");
    }

    @Test
    @DisplayName("defines contribution ownership, merge rules, and manifest references")
    void definesContributionOwnershipAndMergeRules() throws IOException {
      JsonNode schema = JSON.readTree(CONTRIBUTION_SCHEMA.toFile());
      JsonNode properties = schema.path("properties");
      JsonNode example =
          JSON.readTree(
              ROOT.resolve("docs/reference/blueprint-contribution-v1.example.json").toFile());

      assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
      assertThat(textValues(properties.path("kind").path("enum")))
          .containsExactly("COMMON", "CAPABILITY", "PROVIDER", "RUNNER", "REPORTING");
      assertThat(
              textValues(
                  properties
                      .path("configurationContributions")
                      .path("items")
                      .path("properties")
                      .path("mergeRule")
                      .path("enum")))
          .containsExactlyInAnyOrder(
              "APPEND_UNIQUE_BY_ID", "DEEP_MERGE_NO_OVERWRITE", "REQUIRE_EQUAL", "SET_IF_ABSENT");
      assertThat(example.path("dependencies").get(0).path("manifestId").asText()).isEqualTo("WEB");
      assertThat(example.path("ownedPaths").get(0).path("operation").asText()).isEqualTo("CREATE");
    }
  }

  @Nested
  @DisplayName("Safety and determinism")
  class SafetyAndDeterminism {
    @Test
    @DisplayName("requires complete validation and collisions before all mutation")
    void requiresValidationBeforeMutation() throws IOException {
      String contract = Files.readString(CONTRACT);

      assertThat(contract)
          .contains("No destination directory or file may be created, truncated, moved, or deleted")
          .contains("empty write set")
          .contains("SCF_PATH_COLLISION")
          .contains("SCF_CONFIG_COLLISION")
          .contains("SCF_INCOMPLETE_SELECTION")
          .contains("SCF_UNSUPPORTED_IOS");
    }

    @Test
    @DisplayName("keeps starter dependencies separate and bounds generated metadata")
    void keepsDependenciesSeparateAndMetadataBounded() throws IOException {
      String contract = Files.readString(CONTRACT);

      assertThat(contract)
          .contains(
              "starter capability manifest remains the sole authority for dependency topology")
          .contains("cannot add MCP artifacts")
          .contains("SHA-256 content/plan digests")
          .contains("Wall-clock timestamps, random UUIDs")
          .contains("byte-identical content");
    }
  }

  private static int occurrences(String value, String token) {
    return (value.length() - value.replace(token, "").length()) / token.length();
  }

  private static List<String> textValues(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
  }

  private static Path repositoryRoot() {
    Path candidate = Path.of("").toAbsolutePath().normalize();
    while (candidate != null) {
      if (Files.exists(candidate.resolve("codinglair-taf-bom/pom.xml"))) return candidate;
      candidate = candidate.getParent();
    }
    throw new IllegalStateException(
        "Cannot locate repository root from " + Path.of("").toAbsolutePath());
  }
}
