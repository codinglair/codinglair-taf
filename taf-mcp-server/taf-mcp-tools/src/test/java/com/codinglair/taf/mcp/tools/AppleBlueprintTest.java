package com.codinglair.taf.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Request;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Apple consumer blueprint composition")
class AppleBlueprintTest {
  @TempDir Path temporary;
  private final BlueprintCompositionEngine engine = new BlueprintCompositionEngine();

  @Test
  @DisplayName("The Apple alias requires explicit family and retains canonical platform")
  void appleAlias() {
    var request = request("APPLE", "NATIVE", "SIMULATOR", "REMOTE_HOST", "TESTNG", "IPAD", null);
    var plan = plan(request, temporary.resolve("alias"));
    assertThat(plan.valid()).isTrue();
    assertThat(plan.request().mobilePlatform())
        .contains(BlueprintCompositionEngine.MobilePlatform.IPADOS);
    assertThat(
            plan(
                    request("APPLE", "NATIVE", "SIMULATOR", "REMOTE_HOST", "TESTNG", null, null),
                    temporary.resolve("invalid"))
                .writes())
        .isEmpty();
  }

  @Test
  @DisplayName("Apple configuration rendering is independent of the process locale")
  @ResourceLock("java.util.Locale.default")
  void locale() {
    var previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      var plan =
          plan(
              request("IOS", "NATIVE", "SIMULATOR", "REMOTE_HOST", "TESTNG", null, null),
              temporary.resolve("locale"));
      assertThat(content(plan, "src/test/resources/capabilities/mobile.yml"))
          .contains("platform: ios");
    } finally {
      Locale.setDefault(previous);
    }
  }

  static Stream<Arguments> selections() {
    return Stream.of("IOS", "IPADOS")
        .flatMap(
            platform ->
                Stream.of("NATIVE", "HYBRID", "SAFARI")
                    .flatMap(
                        mode ->
                            Stream.of("SIMULATOR", "PHYSICAL")
                                .flatMap(
                                    kind ->
                                        Stream.of("LOCAL_HOST", "REMOTE_HOST", "PROVIDER")
                                            .flatMap(
                                                topology ->
                                                    Stream.of("TESTNG", "CUCUMBER_TESTNG")
                                                        .map(
                                                            runner ->
                                                                Arguments.of(
                                                                    platform, mode, kind, topology,
                                                                    runner))))));
  }

  @ParameterizedTest
  @MethodSource("selections")
  @DisplayName("Every Apple family mode target topology and runner renders deterministically")
  void matrix(String platform, String mode, String kind, String topology, String runner) {
    var request = request(platform, mode, kind, topology, runner, null, null);
    var first = plan(request, temporary.resolve("consumer"));
    var second = plan(request, temporary.resolve("consumer"));
    assertThat(first.valid()).as(first.diagnostics().toString()).isTrue();
    assertThat(first.writes())
        .extracting(BlueprintCompositionEngine.PlannedWrite::sha256)
        .containsExactlyElementsOf(
            second.writes().stream().map(BlueprintCompositionEngine.PlannedWrite::sha256).toList());
    assertThat(first.dependencies())
        .extracting(BlueprintCompositionEngine.Dependency::artifactId)
        .contains("codinglair-taf-starter-mobile")
        .noneMatch(name -> name.contains("mcp"));
    String yaml = content(first, "src/test/resources/capabilities/mobile.yml");
    assertThat(yaml)
        .contains(
            "device-family: " + (platform.equals("IPADOS") ? "IPAD" : "IPHONE"),
            "execution-mode: " + mode,
            "device-kind: " + kind,
            "topology: " + topology);
    if (mode.equals("SAFARI"))
      assertThat(yaml).doesNotContain("bundle-id", "app-reference", "application-mode");
    assertThat(
            content(
                first, "src/test/java/com/example/apple/functional/AppleInteractionExample.java"))
        .contains("extends TafBaseTest", "InteractionTask.verify");
    if (runner.equals("CUCUMBER_TESTNG")) {
      assertThat(content(first, "src/test/java/com/example/apple/bdd/AppleBehaviorRunner.java"))
          .contains("extends AbstractCucumberRunner")
          .doesNotContain("TafBaseTest");
      assertThat(content(first, "pom.xml")).contains("codinglair-taf-runner-cucumber");
    }
    assertThat(temporary.resolve("consumer")).doesNotExist();
  }

  static Stream<Arguments> conflicts() {
    return Stream.of(
        Arguments.of("IOS", "NATIVE", "SIMULATOR", "REMOTE_HOST", "IPAD", null),
        Arguments.of("IPADOS", "NATIVE", "SIMULATOR", "REMOTE_HOST", "IPHONE", null),
        Arguments.of("IOS", "NATIVE", "SIMULATOR", "REMOTE_HOST", null, "UiAutomator2"),
        Arguments.of("ANDROID", "NATIVE", "SIMULATOR", "REMOTE_HOST", null, null),
        Arguments.of("IOS", "UNKNOWN", "SIMULATOR", "REMOTE_HOST", null, null),
        Arguments.of("IOS", "NATIVE", "UNKNOWN", "REMOTE_HOST", null, null),
        Arguments.of("IOS", "NATIVE", "SIMULATOR", "UNKNOWN", null, null));
  }

  @ParameterizedTest
  @MethodSource("conflicts")
  @DisplayName("Conflicting Apple selections return no writes and cannot be published")
  void conflicts(
      String platform,
      String mode,
      String kind,
      String topology,
      String family,
      String automation) {
    var destination = temporary.resolve("invalid");
    var plan =
        plan(request(platform, mode, kind, topology, "TESTNG", family, automation), destination);
    assertThat(plan.valid()).isFalse();
    assertThat(plan.writes()).isEmpty();
    assertThrows(IllegalArgumentException.class, () -> engine.write(plan, destination));
    assertThat(destination).doesNotExist();
  }

  @Test
  @DisplayName("Transport-neutral arguments retain all Apple selections")
  void arguments() {
    var request =
        BlueprintRequestArguments.blueprint(
            Map.ofEntries(
                Map.entry("groupId", "com.example"), Map.entry("artifactId", "apple"),
                Map.entry("basePackage", "com.example.apple"), Map.entry("tafVersion", "1.2.0"),
                Map.entry("capabilities", List.of("MOBILE")), Map.entry("mobilePlatform", "IPADOS"),
                Map.entry("mobileFamily", "IPAD"), Map.entry("mobileMode", "SAFARI"),
                Map.entry("mobileDeviceKind", "PHYSICAL"),
                    Map.entry("mobileTopology", "PROVIDER")));
    assertThat(request.mobileFamily()).isEqualTo("IPAD");
    assertThat(request.mobileMode()).isEqualTo("SAFARI");
    assertThat(request.mobileDeviceKind()).isEqualTo("PHYSICAL");
    assertThat(request.mobileTopology()).isEqualTo("PROVIDER");
  }

  @Test
  @DisplayName("Packaged mode emits a typed matching reference and Safari rejects app modes")
  void applicationModes() {
    for (String kind : List.of("SIMULATOR", "PHYSICAL")) {
      var request =
          new Request(
              "com.example",
              "apple",
              "com.example.apple",
              "1.2.0",
              List.of("MOBILE"),
              null,
              "IOS",
              null,
              "TESTNG",
              "NONE",
              null,
              null,
              "NATIVE",
              kind,
              "REMOTE_HOST",
              "PACKAGED");
      var plan = plan(request, temporary.resolve("packaged"));
      assertThat(plan.valid()).isTrue();
      assertThat(content(plan, "src/test/resources/capabilities/mobile.yml"))
          .contains(
              "application-mode: PACKAGED",
              "app-reference:",
              "kind: SERVER_PATH",
              "build-kind: " + kind);
    }
    var safari =
        new Request(
            "com.example",
            "apple",
            "com.example.apple",
            "1.2.0",
            List.of("MOBILE"),
            null,
            "IOS",
            null,
            "TESTNG",
            "NONE",
            null,
            null,
            "SAFARI",
            "SIMULATOR",
            "REMOTE_HOST",
            "PACKAGED");
    assertThat(plan(safari, temporary.resolve("invalid")).writes()).isEmpty();
  }

  @Test
  @DisplayName(
      "Writes standalone matrix fixtures only to an explicitly supplied evidence directory")
  void fixtures() throws Exception {
    String configured = System.getProperty("scf.apple.fixture.root");
    if (configured == null) return;
    var root = Path.of(configured).toAbsolutePath().normalize();
    Files.createDirectories(root);
    for (String platform : List.of("IOS", "IPADOS")) {
      for (String mode : List.of("NATIVE", "HYBRID", "SAFARI")) {
        String name = platform.toLowerCase() + "-" + mode.toLowerCase();
        var destination = root.resolve(name);
        var request =
            request(
                platform,
                mode,
                platform.equals("IOS") ? "SIMULATOR" : "PHYSICAL",
                mode.equals("NATIVE")
                    ? "LOCAL_HOST"
                    : mode.equals("HYBRID") ? "REMOTE_HOST" : "PROVIDER",
                "CUCUMBER_TESTNG",
                null,
                null);
        var plan = plan(request, destination);
        assertThat(plan.valid()).as(plan.diagnostics().toString()).isTrue();
        assertThat(engine.write(plan, destination).status())
            .isEqualTo(BlueprintCompositionEngine.WriteStatus.WRITTEN);
      }
      var destination = root.resolve(platform.toLowerCase() + "-packaged");
      var packaged =
          new Request(
              "com.example",
              "apple",
              "com.example.apple",
              "1.2.0",
              List.of("MOBILE"),
              null,
              platform,
              null,
              "TESTNG",
              "NONE",
              null,
              null,
              "NATIVE",
              platform.equals("IOS") ? "SIMULATOR" : "PHYSICAL",
              "REMOTE_HOST",
              "PACKAGED");
      var packagedPlan = plan(packaged, destination);
      assertThat(packagedPlan.valid()).isTrue();
      assertThat(engine.write(packagedPlan, destination).status())
          .isEqualTo(BlueprintCompositionEngine.WriteStatus.WRITTEN);
    }
  }

  private static Request request(
      String platform,
      String mode,
      String kind,
      String topology,
      String runner,
      String family,
      String automation) {
    return new Request(
        "com.example",
        "apple",
        "com.example.apple",
        "1.2.0",
        List.of("MOBILE"),
        null,
        platform,
        automation,
        runner,
        "NONE",
        null,
        family,
        mode,
        kind,
        topology);
  }

  private BlueprintCompositionEngine.CompositionPlan plan(Request request, Path destination) {
    return engine.plan(
        request,
        "1.0",
        CapabilityContributionCatalog.contributions(),
        CapabilityContributionCatalog.starterManifest(),
        destination);
  }

  private static String content(BlueprintCompositionEngine.CompositionPlan plan, String path) {
    return plan.writes().stream()
        .filter(write -> write.path().toString().replace('\\', '/').equals(path))
        .findFirst()
        .map(write -> new String(write.content(), StandardCharsets.UTF_8))
        .orElseThrow();
  }
}
