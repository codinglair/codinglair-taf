package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Asset;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Capability;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.CompositionPlan;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.ConfigurationClaim;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Contribution;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Kind;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.MergeRule;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Reporting;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Request;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Runner;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Selector;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.StarterManifest;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.WriteStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Deterministic blueprint composition engine")
class BlueprintCompositionEngineTest {
  private final BlueprintCompositionEngine engine = new BlueprintCompositionEngine();
  @TempDir Path temporary;

  @Nested
  @DisplayName("Normalization and planning")
  class Planning {
    @Test
    @DisplayName("normalizes equivalent requests to byte-identical canonical plans")
    void producesEquivalentGoldenOutput() {
      var destination = temporary.resolve("golden");
      var first =
          engine.plan(request(List.of("web", "mobile")), "1.0", catalog(), manifest(), destination);
      var second =
          engine.plan(
              request(List.of(" MOBILE ", " WEB ")),
              "1.0",
              reversedCatalog(),
              manifest(),
              destination);

      assertThat(first.valid()).isTrue();
      assertThat(second.valid()).isTrue();
      assertThat(first.request()).isEqualTo(second.request());
      assertThat(first.contributionIds())
          .containsExactly("common", "mobile", "web", "testng", "allure");
      assertThat(first.configuration())
          .extracting(BlueprintCompositionEngine.PlannedConfiguration::contributionId)
          .containsExactly("common", "web");
      assertThat(first.writes()).usingRecursiveComparison().isEqualTo(second.writes());
      assertThat(first.writes())
          .extracting(BlueprintCompositionEngine.PlannedWrite::sha256)
          .containsExactly(
              "aaf9ff488e0767da5ea1d56118e6f65a16c5633b0cefc1fa089bd3ab1810613d",
              "b0b9afc17fb9649b10c0acacf5aaabba25f8d27954f1ad949a464d21c26b4aca",
              "9d31a327402ff921a72922a77b2f2a570af25067b281f96d51ff943df493a2e3");
      assertThat(first.request().mobilePlatform())
          .contains(BlueprintCompositionEngine.MobilePlatform.ANDROID);
      assertThat(first.request().mobileAutomationName()).contains("UIAUTOMATOR2");
    }

    @Test
    @DisplayName("resolves dependency coordinates only through the authoritative starter manifest")
    void resolvesManifestCoordinates() {
      var plan =
          engine.plan(
              request(List.of("WEB", "MOBILE")),
              "1.0",
              catalog(),
              manifest(),
              temporary.resolve("manifest"));

      assertThat(plan.dependencies())
          .extracting(BlueprintCompositionEngine.Dependency::artifactId)
          .containsExactly("codinglair-taf-starter-mobile", "codinglair-taf-starter-web");
      assertThat(plan.dependencies())
          .allSatisfy(
              dependency -> {
                assertThat(dependency.groupId()).isEqualTo("com.codinglair.taf");
                assertThat(dependency.version()).isEqualTo("1.2.0");
                assertThat(dependency.artifactId()).doesNotContain("mcp");
              });
    }

    @Test
    @DisplayName("applies common runner reporting and every capability exactly once")
    void selectsEachLayerOnce() {
      var plan =
          engine.plan(
              request(List.of("WEB", "MOBILE")),
              "1.0",
              catalog(),
              manifest(),
              temporary.resolve("once"));

      assertThat(plan.contributionIds()).doesNotHaveDuplicates();
      assertThat(plan.contributionIds())
          .containsExactly("common", "mobile", "web", "testng", "allure");
    }
  }

  @Nested
  @DisplayName("Validation failures")
  class Validation {
    @ParameterizedTest(name = "rejects unsupported {0} selection")
    @CsvSource({"runner,UNKNOWN,,", "reporting,,UNKNOWN,", "testdefinitions,,,UNKNOWN"})
    @DisplayName("reports unsupported defaultable enum selections")
    void rejectsUnsupportedDefaultableSelections(
        String subject, String runner, String reporting, String definitions) {
      var request =
          new Request(
              "com.example",
              "sample",
              "com.example.sample",
              "1.2.0",
              List.of("WEB"),
              null,
              null,
              null,
              runner,
              reporting,
              definitions);

      var plan = engine.plan(request, "1.0", catalog(), manifest(), temporary.resolve(subject));

      assertThat(plan.valid()).isFalse();
      assertThat(plan.writes()).isEmpty();
      assertThat(plan.diagnostics())
          .singleElement()
          .satisfies(
              diagnostic -> {
                assertThat(diagnostic.code()).isEqualTo("SCF_UNSUPPORTED_SELECTION");
                assertThat(diagnostic.message()).containsIgnoringCase(subject);
              });
    }

    @Test
    @DisplayName("derives missing messaging provider guidance from supported enum values")
    void reportsSupportedMessagingProviders() {
      var request = request(List.of("MESSAGING"));

      var plan =
          engine.plan(request, "1.0", catalog(), manifest(), temporary.resolve("providerless"));

      assertThat(plan.valid()).isFalse();
      assertThat(plan.writes()).isEmpty();
      assertThat(plan.diagnostics())
          .singleElement()
          .extracting(BlueprintCompositionEngine.Diagnostic::correctiveAction)
          .isEqualTo("Select one of: AWS, JMS, KAFKA, RABBITMQ.");
    }

    @Test
    @DisplayName("reports unsupported iOS before mutation")
    void rejectsIos() {
      var request =
          new Request(
              "com.example",
              "sample",
              "com.example.sample",
              "1.2.0",
              List.of("MOBILE"),
              null,
              "ios",
              null,
              null,
              null,
              null);
      var destination = temporary.resolve("ios");

      var plan = engine.plan(request, "1.0", catalog(), manifest(), destination);

      assertThat(plan.valid()).isFalse();
      assertThat(plan.writes()).isEmpty();
      assertThat(plan.diagnostics())
          .extracting(BlueprintCompositionEngine.Diagnostic::code)
          .contains("SCF_UNSUPPORTED_IOS");
      assertThat(destination).doesNotExist();
    }

    @Test
    @DisplayName("reports path and configuration collisions with an empty write set")
    void rejectsAllCollisions() {
      var conflicting =
          new Contribution(
              "conflict",
              "1.0",
              Kind.CAPABILITY,
              new Selector(Kind.CAPABILITY, Capability.WEB, null, null, null),
              201,
              List.of("WEB"),
              List.of(new Asset("README.md", "different\n", false)),
              List.of(
                  new ConfigurationClaim(
                      "application.yaml", "/spring", MergeRule.SET_IF_ABSENT, "other")));
      var contributions = new java.util.ArrayList<>(catalog());
      contributions.add(conflicting);
      var destination = temporary.resolve("collision");

      var plan =
          engine.plan(request(List.of("WEB")), "1.0", contributions, manifest(), destination);

      assertThat(plan.valid()).isFalse();
      assertThat(plan.writes()).isEmpty();
      assertThat(plan.diagnostics())
          .extracting(BlueprintCompositionEngine.Diagnostic::code)
          .contains("SCF_PATH_COLLISION", "SCF_CONFIG_COLLISION", "SCF_UNSUPPORTED_SELECTION");
      assertThat(destination).doesNotExist();
    }

    @Test
    @DisplayName("rejects missing manifest references without synthesizing a dependency")
    void rejectsMissingManifestReference() {
      var incomplete =
          new StarterManifest("com.codinglair.taf", Map.of("WEB", "codinglair-taf-starter-web"));
      var plan =
          engine.plan(
              request(List.of("MOBILE")),
              "1.0",
              catalog(),
              incomplete,
              temporary.resolve("missing"));

      assertThat(plan.valid()).isFalse();
      assertThat(plan.dependencies()).isEmpty();
      assertThat(plan.writes()).isEmpty();
      assertThat(plan.diagnostics())
          .extracting(BlueprintCompositionEngine.Diagnostic::code)
          .contains("SCF_MANIFEST_REFERENCE");
    }

    @Test
    @DisplayName("rejects duplicate normalized capability selections")
    void rejectsDuplicateCapabilities() {
      var destination = temporary.resolve("duplicate");
      var plan =
          engine.plan(request(List.of("WEB", " web ")), "1.0", catalog(), manifest(), destination);

      assertThat(plan.valid()).isFalse();
      assertThat(plan.writes()).isEmpty();
      assertThat(plan.diagnostics())
          .extracting(BlueprintCompositionEngine.Diagnostic::code)
          .contains("SCF_INVALID_REQUEST");
      assertThat(destination).doesNotExist();
    }

    @Test
    @DisplayName("leaves an existing target byte-for-byte unchanged")
    void preservesExistingTarget() throws Exception {
      var destination = Files.createDirectory(temporary.resolve("existing"));
      var userFile = destination.resolve("user.txt");
      Files.writeString(userFile, "keep");
      var plan = engine.plan(request(List.of("WEB")), "1.0", catalog(), manifest(), destination);

      var result = engine.write(plan, destination);

      assertThat(result.status()).isEqualTo(WriteStatus.CONFLICT);
      assertThat(Files.readString(userFile)).isEqualTo("keep");
      assertThat(destination.resolve("README.md")).doesNotExist();
    }
  }

  @Nested
  @DisplayName("Mutation boundary")
  class Mutation {
    @Test
    @DisplayName("publishes the complete plan and repeated execution does not overwrite")
    void writesOnce() throws Exception {
      var destination = temporary.resolve("written");
      var plan =
          engine.plan(request(List.of("WEB", "MOBILE")), "1.0", catalog(), manifest(), destination);

      assertThat(engine.write(plan, destination).status()).isEqualTo(WriteStatus.WRITTEN);
      assertThat(engine.write(plan, destination).status()).isEqualTo(WriteStatus.CONFLICT);
      assertThat(Files.readString(destination.resolve("README.md"))).isEqualTo("sample\n");
      assertThat(
              Files.readString(destination.resolve("src/main/java/com/example/sample/Mobile.java")))
          .contains("package com.example.sample;");
    }

    @Test
    @DisplayName("allows exactly one concurrent publisher and never exposes a partial destination")
    void concurrentWritesHaveOneWinner() {
      assertTimeoutPreemptively(
          Duration.ofSeconds(5),
          () -> {
            var destination = temporary.resolve("concurrent");
            CompositionPlan plan =
                engine.plan(
                    request(List.of("WEB", "MOBILE")), "1.0", catalog(), manifest(), destination);
            try (var executor = Executors.newFixedThreadPool(2)) {
              List<Callable<BlueprintCompositionEngine.WriteResult>> calls =
                  List.of(
                      () -> engine.write(plan, destination), () -> engine.write(plan, destination));
              var results =
                  executor.invokeAll(calls).stream()
                      .map(
                          future -> {
                            try {
                              return future.get().status();
                            } catch (Exception failure) {
                              throw new AssertionError(failure);
                            }
                          })
                      .toList();
              assertThat(results)
                  .containsExactlyInAnyOrder(WriteStatus.WRITTEN, WriteStatus.CONFLICT);
            }
            assertThat(destination.resolve("README.md")).exists();
            assertThat(destination.resolve("src/main/java/com/example/sample/Mobile.java"))
                .exists();
            assertThat(destination.resolve("src/test/java/com/example/sample/WebTest.java"))
                .exists();
          });
    }
  }

  private static Request request(List<String> capabilities) {
    return new Request(
        " com.example ",
        " sample ",
        " com.example.sample ",
        " 1.2.0 ",
        capabilities,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static StarterManifest manifest() {
    return new StarterManifest(
        "com.codinglair.taf",
        Map.of(
            "WEB", "codinglair-taf-starter-web",
            "MOBILE", "codinglair-taf-starter-mobile"));
  }

  private static List<Contribution> reversedCatalog() {
    var values = new java.util.ArrayList<>(catalog());
    java.util.Collections.reverse(values);
    return values;
  }

  private static List<Contribution> catalog() {
    return List.of(
        contribution(
            "web",
            Kind.CAPABILITY,
            220,
            new Selector(Kind.CAPABILITY, Capability.WEB, null, null, null),
            List.of("WEB"),
            List.of(
                new Asset(
                    "src/test/java/__PACKAGE_PATH__/WebTest.java",
                    "package __BASE_PACKAGE__;\nfinal class WebTest {}\n",
                    false)),
            List.of(
                new ConfigurationClaim(
                    "application.yaml", "/taf/web", MergeRule.SET_IF_ABSENT, "enabled"))),
        contribution(
            "allure",
            Kind.REPORTING,
            500,
            new Selector(Kind.REPORTING, null, null, null, Reporting.ALLURE),
            List.of(),
            List.of(),
            List.of()),
        contribution(
            "common",
            Kind.COMMON,
            100,
            new Selector(Kind.COMMON, null, null, null, null),
            List.of(),
            List.of(new Asset("README.md", "__ARTIFACT_ID__\n", false)),
            List.of(
                new ConfigurationClaim(
                    "application.yaml", "/spring", MergeRule.SET_IF_ABSENT, "none"))),
        contribution(
            "mobile",
            Kind.CAPABILITY,
            210,
            new Selector(Kind.CAPABILITY, Capability.MOBILE, null, null, null),
            List.of("MOBILE"),
            List.of(
                new Asset(
                    "src/main/java/__PACKAGE_PATH__/Mobile.java",
                    "package __BASE_PACKAGE__;\nfinal class Mobile {}\n",
                    false)),
            List.of()),
        contribution(
            "testng",
            Kind.RUNNER,
            400,
            new Selector(Kind.RUNNER, null, null, Runner.TESTNG, null),
            List.of(),
            List.of(),
            List.of()));
  }

  private static Contribution contribution(
      String id,
      Kind kind,
      int order,
      Selector selector,
      List<String> manifestIds,
      List<Asset> assets,
      List<ConfigurationClaim> claims) {
    return new Contribution(id, "1.0", kind, selector, order, manifestIds, assets, claims);
  }
}
