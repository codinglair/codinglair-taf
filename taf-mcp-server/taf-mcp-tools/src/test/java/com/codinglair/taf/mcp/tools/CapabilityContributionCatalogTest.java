package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.CompositionPlan;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Request;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Capability contribution catalog")
class CapabilityContributionCatalogTest {
  private static final String TAF_VERSION = "1.1.0";
  private final BlueprintCompositionEngine engine = new BlueprintCompositionEngine();

  @Nested
  @DisplayName("Single capability projects")
  class SingleCapabilityProjects {
    @ParameterizedTest(name = "{0} selects only its public starter/provider")
    @MethodSource(
        "com.codinglair.taf.mcp.tools.CapabilityContributionCatalogTest#singleCapabilities")
    @DisplayName("Each supported capability produces a complete collision-free project")
    void createsCompleteProject(
        String capability, String provider, String expectedArtifact, String expectedExample) {
      CompositionPlan plan = plan(List.of(capability), provider);

      assertThat(plan.valid()).isTrue();
      assertThat(plan.dependencies())
          .extracting(BlueprintCompositionEngine.Dependency::artifactId)
          .containsExactly(expectedArtifact)
          .noneMatch(artifact -> artifact.contains("mcp"));
      assertThat(paths(plan))
          .contains("pom.xml", "src/test/java/com/example/generated/BlueprintPreflightTest.java")
          .anyMatch(path -> path.endsWith(expectedExample + ".java"));
      assertThat(content(plan, "pom.xml"))
          .contains("<artifactId>" + expectedArtifact + "</artifactId>")
          .contains("<type>pom</type>")
          .doesNotContain("taf-mcp", "playwright")
          .doesNotContain("__TAF_DEPENDENCIES__");
    }
  }

  @Nested
  @DisplayName("Composition and isolation")
  class CompositionAndIsolation {
    @Test
    @DisplayName("Web API and Database compose with one common foundation")
    void composesWebApiAndDatabase() {
      CompositionPlan plan = plan(List.of("WEB", "API", "DATABASE"), null);

      assertThat(plan.valid()).isTrue();
      assertThat(plan.contributionIds()).containsOnlyOnce("common");
      assertThat(paths(plan)).doesNotHaveDuplicates();
      assertThat(content(plan, "pom.xml"))
          .contains(
              "codinglair-taf-starter-web",
              "codinglair-taf-starter-api",
              "codinglair-taf-starter-database")
          .doesNotContain("taf-web-playwright", "taf-api-rest", "taf-database", "taf-mcp");
    }

    @ParameterizedTest
    @MethodSource(
        "com.codinglair.taf.mcp.tools.CapabilityContributionCatalogTest#nonWebCapabilities")
    @DisplayName("A non-Web project has no Playwright assumption")
    void excludesPlaywrightFromNonWebProjects(String capability, String provider) {
      CompositionPlan plan = plan(List.of(capability), provider);

      assertThat(plan.valid()).isTrue();
      assertThat(allContent(plan).toLowerCase()).doesNotContain("playwright");
      assertThat(paths(plan)).noneMatch(path -> path.toLowerCase().contains("web"));
    }

    @Test
    @DisplayName("Equivalent requests have stable golden output")
    void producesStableGoldenOutput() {
      CompositionPlan first = plan(List.of("DATABASE", "API", "WEB"), null);
      CompositionPlan second = plan(List.of("WEB", "DATABASE", "API"), null);

      assertThat(first.writes())
          .extracting(BlueprintCompositionEngine.PlannedWrite::sha256)
          .containsExactlyElementsOf(
              second.writes().stream()
                  .map(BlueprintCompositionEngine.PlannedWrite::sha256)
                  .toList());
      assertThat(content(first, "README.md"))
          .isEqualTo(content(second, "README.md"))
          .contains("secret references, never values");
    }
  }

  @Nested
  @DisplayName("Unsupported selections")
  class UnsupportedSelections {
    @Test
    @DisplayName("Messaging without a provider fails before generation")
    void rejectsProviderlessMessaging() {
      CompositionPlan plan = plan(List.of("MESSAGING"), null);

      assertThat(plan.valid()).isFalse();
      assertThat(plan.writes()).isEmpty();
      assertThat(plan.diagnostics())
          .extracting(BlueprintCompositionEngine.Diagnostic::code)
          .contains("SCF_INCOMPLETE_SELECTION");
    }

    @Test
    @DisplayName("Explicit iOS fails before generation with corrective guidance")
    void rejectsIos() {
      CompositionPlan plan = plan(List.of("MOBILE"), null, "IOS");

      assertThat(plan.valid()).isFalse();
      assertThat(plan.writes()).isEmpty();
      assertThat(plan.diagnostics())
          .anySatisfy(
              diagnostic -> {
                assertThat(diagnostic.code()).isEqualTo("SCF_UNSUPPORTED_IOS");
                assertThat(diagnostic.correctiveAction()).contains("Android/UiAutomator2");
              });
    }

    @Test
    @DisplayName("Mobile defaults to Android and UiAutomator2")
    void defaultsMobilePlatform() {
      CompositionPlan plan = plan(List.of("MOBILE"), null);

      assertThat(plan.valid()).isTrue();
      assertThat(plan.request().mobilePlatform())
          .contains(BlueprintCompositionEngine.MobilePlatform.ANDROID);
      assertThat(plan.request().mobileAutomationName()).contains("UIAUTOMATOR2");
      assertThat(allContent(plan)).contains("UiAutomator2");
    }
  }

  @Test
  @DisplayName("Writes generated verification fixtures when explicitly requested")
  void writesVerificationFixtures(@TempDir Path unused) throws Exception {
    String configuredRoot = System.getProperty("scf.fixture.root");
    if (configuredRoot == null) {
      assertThat(unused).exists();
      return;
    }
    Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
    Files.createDirectories(root);
    for (Arguments arguments : singleCapabilities().toList()) {
      Object[] values = arguments.get();
      String capability = (String) values[0];
      String provider = (String) values[1];
      writeFixture(root.resolve(capability.toLowerCase()), List.of(capability), provider);
    }
    writeFixture(root.resolve("web-api-database"), List.of("WEB", "API", "DATABASE"), null);
  }

  private void writeFixture(Path destination, List<String> capabilities, String provider)
      throws Exception {
    CompositionPlan plan = plan(capabilities, provider, null, destination);
    assertThat(plan.valid()).isTrue();
    assertThat(engine.write(plan, destination).status())
        .isEqualTo(BlueprintCompositionEngine.WriteStatus.WRITTEN);
  }

  private CompositionPlan plan(List<String> capabilities, String provider) {
    return plan(capabilities, provider, null);
  }

  private CompositionPlan plan(List<String> capabilities, String provider, String platform) {
    return plan(capabilities, provider, platform, Path.of("target", "not-written"));
  }

  private CompositionPlan plan(
      List<String> capabilities, String provider, String platform, Path destination) {
    return engine.plan(
        new Request(
            "com.example",
            "generated-project",
            "com.example.generated",
            TAF_VERSION,
            capabilities,
            provider,
            platform,
            null,
            null,
            null,
            null),
        CapabilityContributionCatalog.BLUEPRINT_VERSION,
        CapabilityContributionCatalog.contributions(),
        CapabilityContributionCatalog.starterManifest(),
        destination);
  }

  private static Stream<Arguments> singleCapabilities() {
    return Stream.of(
        Arguments.of("WEB", null, "codinglair-taf-starter-web", "WebPageObjectExample"),
        Arguments.of("API", null, "codinglair-taf-starter-api", "ApiObjectModelExample"),
        Arguments.of(
            "DATABASE", null, "codinglair-taf-starter-database", "NamedDatabaseConnectionExample"),
        Arguments.of(
            "MESSAGING",
            "KAFKA",
            "codinglair-taf-starter-messaging-kafka",
            "NamedMessagingProviderExample"),
        Arguments.of("MOBILE", null, "codinglair-taf-starter-mobile", "AndroidObjectModelExample"));
  }

  private static Stream<Arguments> nonWebCapabilities() {
    return Stream.of(
        Arguments.of("API", null),
        Arguments.of("DATABASE", null),
        Arguments.of("MESSAGING", "KAFKA"),
        Arguments.of("MOBILE", null));
  }

  private static List<String> paths(CompositionPlan plan) {
    return plan.writes().stream().map(write -> write.path().toString().replace('\\', '/')).toList();
  }

  private static String content(CompositionPlan plan, String path) {
    return plan.writes().stream()
        .filter(write -> write.path().toString().replace('\\', '/').equals(path))
        .findFirst()
        .map(write -> new String(write.content(), StandardCharsets.UTF_8))
        .orElseThrow();
  }

  private static String allContent(CompositionPlan plan) {
    return plan.writes().stream()
        .map(write -> new String(write.content(), StandardCharsets.UTF_8))
        .reduce("", (left, right) -> left + "\n" + right);
  }
}
