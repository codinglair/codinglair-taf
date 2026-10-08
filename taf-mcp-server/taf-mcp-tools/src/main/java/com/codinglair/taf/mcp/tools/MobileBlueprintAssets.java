package com.codinglair.taf.mcp.tools;

import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Asset;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.NormalizedRequest;
import com.codinglair.taf.mcp.tools.BlueprintCompositionEngine.Runner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Apple assets use public consumer contracts and never contact execution infrastructure. */
final class MobileBlueprintAssets {
  private MobileBlueprintAssets() {}

  static List<Asset> assets(NormalizedRequest request) {
    var assets = new ArrayList<Asset>();
    for (String path :
        List.of(
            "src/test/java/__PACKAGE_PATH__/configuration/MobileConfiguration.java",
            "src/test/java/__PACKAGE_PATH__/configuration/InteractionProperties.java",
            "src/test/java/__PACKAGE_PATH__/screen/apple/AppleScreen.java",
            "src/test/java/__PACKAGE_PATH__/task/InteractionTask.java",
            "src/test/java/__PACKAGE_PATH__/context/MobileContextTest.java",
            "src/test/java/__PACKAGE_PATH__/task/InteractionTaskTest.java",
            "src/test/java/__PACKAGE_PATH__/functional/AppleInteractionExample.java",
            "src/test/resources/testng-apple.xml",
            "APPLE.md")) {
      assets.add(template(path));
    }
    String config =
        """
        taf:
          mobile:
            apple:
              enabled: true
              controllers:
                primary:
                  platform: %s
                  device-family: %s
                  automation-name: XCUITest
                  execution-mode: %s
                  device-kind: %s
                  topology: %s
                  server-url: '${APPLE_SERVER_URL:http://127.0.0.1:4723}'
                  device-name: '${APPLE_DEVICE_NAME:consumer-target}'
                  device-id: '${APPLE_DEVICE_ID:UNRESOLVED}'
        %s
        example:
          interaction:
            accessibility-id: '${APPLE_ACCESSIBILITY_ID:}'
            expected-text: '${APPLE_EXPECTED_TEXT:}'
            webview: '${APPLE_WEBVIEW:}'
            url: '${APPLE_TEST_URL:}'
            css-selector: '${APPLE_CSS_SELECTOR:}'
        """
            .formatted(
                request.mobilePlatform().orElseThrow().name().toLowerCase(Locale.ROOT),
                request.mobileFamily(),
                request.mobileMode(),
                request.mobileDeviceKind(),
                request.mobileTopology(),
                applicationConfiguration(request));
    assets.add(new Asset("src/test/resources/capabilities/mobile.yml", config, false));
    if (request.runner() == Runner.CUCUMBER_TESTNG) {
      assets.add(template("src/test/java/__PACKAGE_PATH__/bdd/AppleBehaviorRunner.java"));
      assets.add(template("src/test/java/__PACKAGE_PATH__/bdd/AppleSteps.java"));
      assets.add(template("src/test/java/__PACKAGE_PATH__/bdd/AppleObjectFactory.java"));
      assets.add(template("src/test/java/__PACKAGE_PATH__/bdd/AppleCompositionTest.java"));
      assets.add(
          new Asset(
              "src/test/resources/META-INF/services/io.cucumber.core.backend.ObjectFactory",
              "__BASE_PACKAGE__.bdd.AppleObjectFactory\n",
              false));
      assets.add(
          new Asset(
              "src/test/resources/features/apple.feature",
              "Feature: Consumer Apple interaction\n  Scenario: Selected target responds\n    Then the selected Apple interaction succeeds\n",
              false));
    }
    return List.copyOf(assets);
  }

  private static String applicationConfiguration(NormalizedRequest request) {
    if (request.mobileMode().equals("SAFARI")) return "";
    String base =
        "          application-mode: "
            + request.mobileApplicationMode()
            + "\n          bundle-id: '${APPLE_BUNDLE_ID:UNRESOLVED}'";
    if (!"PACKAGED".equals(request.mobileApplicationMode())) return base;
    return base
        + "\n          app-reference:\n            kind: SERVER_PATH\n"
        + "            value: '${APPLE_APP_REFERENCE:/UNRESOLVED/Consumer.app}'\n            build-kind: "
        + request.mobileDeviceKind();
  }

  private static Asset template(String path) {
    try (var stream =
        MobileBlueprintAssets.class.getResourceAsStream("/mobile-blueprint/" + path)) {
      if (stream == null)
        throw new IllegalStateException("Missing approved mobile blueprint asset");
      return new Asset(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8), false);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read approved mobile blueprint asset", failure);
    }
  }
}
