package com.codinglair.taf.mcp.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MCP v1 schema compatibility")
class McpSchemaCompatibilityTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SCHEMA_ROOT = "/META-INF/taf/mcp/schema/v1/";

  @ParameterizedTest(name = "{0} accepts {1}")
  @MethodSource("validFixtures")
  @DisplayName("Published v1 fixtures validate")
  void publishedFixturesValidate(String schemaName, String fixture) throws IOException {
    assertThat(validate(schemaName, "/fixtures/v1/valid/" + fixture)).isEmpty();
  }

  @ParameterizedTest(name = "{0} rejects {1}")
  @MethodSource("invalidFixtures")
  @DisplayName("Unsafe or incomplete fixtures are rejected")
  void unsafeFixturesAreRejected(String schemaName, String fixture) throws IOException {
    assertThat(validate(schemaName, "/fixtures/v1/invalid/" + fixture)).isNotEmpty();
  }

  @ParameterizedTest(name = "rejects secret-bearing argument name {0}")
  @MethodSource("secretBearingArgumentNames")
  @DisplayName("Conventional secret-bearing argument names are rejected")
  void conventionalSecretBearingArgumentNamesAreRejected(String argumentName)
      throws IOException {
    ObjectNode request =
        (ObjectNode) read("/fixtures/v1/valid/tool-request-validate.json").deepCopy();
    ((ObjectNode) request.path("arguments")).put(argumentName, "canary-secret-value");

    assertThat(schema("tool-request.schema.json").validate(request)).isNotEmpty();
  }

  @Test
  @DisplayName("Every published v1 schema parses and compiles")
  void everyPublishedSchemaCompiles() throws IOException {
    for (String name :
        Set.of(
            "common.schema.json",
            "capability-catalog.schema.json",
            "tool-request.schema.json",
            "tool-response.schema.json",
            "resource.schema.json",
            "prompt.schema.json")) {
      assertThat(schema(name)).as(name).isNotNull();
    }
  }

  @Test
  @DisplayName("Catalog covers every approved coarse-grained workflow with operational semantics")
  void catalogCoversApprovedWorkflows() throws IOException {
    JsonNode catalog = read("/META-INF/taf/mcp/catalog/v1/capabilities.json");
    JsonSchema schema = schema("capability-catalog.schema.json");
    assertThat(schema.validate(catalog)).isEmpty();
    assertThat(catalog.path("catalogVersion").textValue()).isEqualTo("1.0.0");

    Set<String> expected =
        Set.of(
            "discover",
            "validate",
            "scaffold",
            "compile",
            "build",
            "execute",
            "inspect",
            "retrieve",
            "cancel",
            "report",
            "diagnose");
    Set<String> actual =
        JSON.convertValue(
            catalog.path("tools").findValues("name"),
            JSON.getTypeFactory().constructCollectionType(Set.class, String.class));
    assertThat(actual).isEqualTo(expected);
    catalog
        .path("tools")
        .forEach(
            tool ->
                assertThat(tool.path("timeout").path("maximumSeconds").asInt())
                    .isGreaterThanOrEqualTo(
                        tool.path("timeout").path("defaultSeconds").asInt()));
  }

  private Set<?> validate(String schemaName, String fixture) throws IOException {
    return schema(schemaName).validate(read(fixture));
  }

  private JsonSchema schema(String name) throws IOException {
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(read(SCHEMA_ROOT + name));
  }

  private JsonNode read(String resource) throws IOException {
    try (InputStream input = McpSchemaCompatibilityTest.class.getResourceAsStream(resource)) {
      assertThat(input).as("classpath resource %s", resource).isNotNull();
      return JSON.readTree(input);
    }
  }

  private static Stream<Arguments> validFixtures() {
    return Stream.of(
        Arguments.of("tool-request.schema.json", "tool-request-validate.json"),
        Arguments.of("tool-request.schema.json", "tool-request-aws.json"),
        Arguments.of("tool-response.schema.json", "tool-response-accepted.json"),
        Arguments.of("resource.schema.json", "resource-report.json"),
        Arguments.of("resource.schema.json", "resource-report-page.json"),
        Arguments.of("prompt.schema.json", "prompt-failure-analysis.json"),
        Arguments.of("prompt.schema.json", "prompt-execution-summary.json"),
        Arguments.of("prompt.schema.json", "prompt-environment-triage.json"));
  }

  private static Stream<Arguments> invalidFixtures() {
    return Stream.of(
        Arguments.of("tool-request.schema.json", "tool-request-secret-value.json"),
        Arguments.of("tool-response.schema.json", "tool-response-accepted-without-job.json"));
  }

  private static Stream<String> secretBearingArgumentNames() {
    return Stream.of(
        "password",
        "passphrase",
        "secret",
        "secretValue",
        "token",
        "tokenValue",
        "apiKey",
        "accessToken",
        "authToken",
        "bearerToken",
        "credential",
        "credentials",
        "privateKey",
        "serviceAccountKey",
        "connectionString");
  }
}
