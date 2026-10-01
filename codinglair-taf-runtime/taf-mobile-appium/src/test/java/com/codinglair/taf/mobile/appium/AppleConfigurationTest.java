package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.mobile.ApplicationMode;
import com.codinglair.taf.mobile.MobileDeviceFamily;
import com.codinglair.taf.mobile.MobileDeviceKind;
import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.MobileTopology;
import com.codinglair.taf.mobile.appium.configuration.AppleAuthentication.Mechanism;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings.AppReference;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings.ReferenceKind;
import com.codinglair.taf.mobile.appium.configuration.AppleWdaSettings.BuildMode;
import com.codinglair.taf.mobile.appium.platform.ApplePlatformStrategy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Apple configuration and capability contracts")
class AppleConfigurationTest {
  static AppleControllerSettings valid() {
    var settings = new AppleControllerSettings();
    settings.setPlatform("ios");
    settings.setDeviceKind(MobileDeviceKind.SIMULATOR);
    settings.setDeviceName("fixture");
    settings.setServerUrl(URI.create("http://127.0.0.1:4723/custom/wd/hub"));
    settings.setBundleId("com.example.fixture");
    return settings;
  }

  @Nested
  @DisplayName("Platform and application routing")
  class Routing {
    @ParameterizedTest
    @MethodSource("com.codinglair.taf.mobile.appium.AppleConfigurationTest#routes")
    @DisplayName("routes both families and kinds across modes and topologies")
    void routes(
        MobileDeviceFamily family,
        MobileDeviceKind kind,
        MobileExecutionMode mode,
        MobileTopology topology) {
      var settings = valid();
      settings.setPlatform(family == MobileDeviceFamily.IPHONE ? "ios" : "ipados");
      settings.setDeviceKind(kind);
      settings.setDeviceId("authorized-fixture");
      settings.setExecutionMode(mode);
      settings.setTopology(topology);
      if (mode == MobileExecutionMode.SAFARI) settings.setBundleId(null);
      var options = new ApplePlatformStrategy().options(settings);
      assertThat(settings.family()).isEqualTo(family);
      assertThat(options.asMap().get("platformName")).isEqualTo("iOS");
      assertThat(options.getCapability("appium:automationName")).isEqualTo("XCUITest");
      assertThat(options.getCapability("appium:noReset")).isEqualTo(true);
      assertThat(options.getCapability("browserName"))
          .isEqualTo(mode == MobileExecutionMode.SAFARI ? "Safari" : null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"android", "macos", "tvos", "watchos", "", "apple"})
    @DisplayName("rejects unsupported platforms and generic Apple without family")
    void invalidPlatform(String platform) {
      var settings = valid();
      settings.setPlatform(platform);
      assertThrows(IllegalArgumentException.class, settings::validate);
    }

    @ParameterizedTest
    @MethodSource("com.codinglair.taf.mobile.appium.AppleConfigurationTest#invalidSettings")
    @DisplayName("rejects conflicts and invalid targets before driver creation")
    void invalid(Consumer<AppleControllerSettings> mutation) {
      var settings = valid();
      mutation.accept(settings);
      assertThrows(
          IllegalArgumentException.class, () -> new ApplePlatformStrategy().options(settings));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SERVER_PATH", "AUTHORIZED_URL", "PROVIDER_UPLOAD"})
    @DisplayName(
        "passes explicit server application references without local path conversion or upload")
    void packageReferences(ReferenceKind kind) {
      var settings = valid();
      settings.setApplicationMode(ApplicationMode.PACKAGED);
      settings.setTopology(MobileTopology.PROVIDER);
      String value =
          switch (kind) {
            case SERVER_PATH -> "/server/apps/Fixture.app";
            case AUTHORIZED_URL -> "https://apps.example.test/fixture.zip";
            case PROVIDER_UPLOAD -> "provider://already-uploaded";
          };
      settings.setAppReference(new AppReference(kind, value, MobileDeviceKind.SIMULATOR));
      assertThat(new ApplePlatformStrategy().options(settings).getCapability("appium:app"))
          .isEqualTo(value);
    }
  }

  static Stream<Arguments> routes() {
    return Stream.of(MobileDeviceFamily.values())
        .flatMap(
            family ->
                Stream.of(MobileDeviceKind.values())
                    .flatMap(
                        kind ->
                            Stream.of(MobileExecutionMode.values())
                                .flatMap(
                                    mode ->
                                        Stream.of(MobileTopology.values())
                                            .map(
                                                topology ->
                                                    Arguments.of(family, kind, mode, topology)))));
  }

  static Stream<Consumer<AppleControllerSettings>> invalidSettings() {
    return Stream.of(
        s -> s.setDeviceFamily(MobileDeviceFamily.IPAD),
        s -> s.setAutomationName("UiAutomator2"),
        s -> s.setDeviceKind(null),
        s -> s.setDeviceName(" "),
        s -> s.setDeviceKind(MobileDeviceKind.PHYSICAL),
        s -> s.setServerUrl(URI.create("http://user:CANARY@localhost/path")),
        s -> s.setServerUrl(URI.create("http://localhost/path?token=CANARY")),
        s -> s.setServerUrl(URI.create("http://localhost/path#fragment")),
        s -> s.setCommandTimeout(Duration.ZERO),
        s -> s.setCommandTimeout(Duration.ofMinutes(11)),
        s -> s.setReadinessTimeout(Duration.ZERO),
        s -> s.setCleanupTimeout(Duration.ofSeconds(-1)),
        s -> s.setContextTimeout(Duration.ofMinutes(11)),
        s -> s.getWda().setLocalPort(0),
        s -> s.getWda().setMjpegPort(65536),
        s -> s.getWda().setDerivedDataPath("C:/client/path"),
        s -> s.getWda().setBuildMode(BuildMode.PREBUILT),
        s -> s.getWda().setBuildMode(BuildMode.RUNNING),
        s -> {
          s.setAutoAcceptAlerts(true);
          s.setAutoDismissAlerts(true);
        },
        s -> s.setVideo(true),
        s -> s.setExecutionMode(MobileExecutionMode.SAFARI),
        s -> {
          s.setExecutionMode(MobileExecutionMode.SAFARI);
          s.setBundleId(null);
          s.setTerminateAppOnClose(false);
        },
        s -> s.setApplicationMode(ApplicationMode.PACKAGED),
        s -> {
          s.setApplicationMode(ApplicationMode.PACKAGED);
          s.setAppReference(
              new AppReference(
                  ReferenceKind.SERVER_PATH, "/Fixture.ipa", MobileDeviceKind.PHYSICAL));
        },
        s ->
            s.setProviderOptions(
                Map.of("appium:options", Map.of("automationName", "UiAutomator2"))),
        s -> s.setProviderOptions(Map.of("cloud:options", Map.of("bundleId", "other"))),
        s -> s.setProviderSelection(Map.of("platformName", "Android")));
  }

  @Nested
  @DisplayName("Layer merging and JSON ownership")
  class Merging {
    @Test
    @DisplayName("merges partial application references and provider target capabilities")
    void nestedConfiguration() {
      var base = valid();
      base.setApplicationMode(ApplicationMode.PACKAGED);
      base.setAppReference(
          new AppReference(ReferenceKind.SERVER_PATH, "/base.app", MobileDeviceKind.SIMULATOR));
      base.setTopology(MobileTopology.PROVIDER);
      base.setProviderOptions(Map.of("cloud:options", Map.of("build", 7)));
      base.setProviderSelection(Map.of("cloud:options", Map.of("model", "authorized")));
      var named = new AppleControllerSettings();
      named.setAppReference(new AppReference(null, "/named.app", null));
      var result = AppleControllerSettings.resolve(base, named, null);
      var options = new ApplePlatformStrategy().options(result);
      assertThat(options.getCapability("appium:app")).isEqualTo("/named.app");
      assertThat(options.getCapability("cloud:options"))
          .isEqualTo(Map.of("build", 7, "model", "authorized"));
      result.getWda().setLocalPort(9999);
      assertThat(base.getWda().getLocalPort()).isNull();
    }

    @Test
    @DisplayName("accepts explicit generic Apple family and fails closed for secret transport")
    void genericAndAuthentication() {
      var settings = valid();
      settings.setPlatform("apple");
      settings.setDeviceFamily(MobileDeviceFamily.IPAD);
      assertThat(settings.family()).isEqualTo(MobileDeviceFamily.IPAD);
      settings.getAuthentication().setMechanism(Mechanism.BASIC);
      assertThrows(IllegalArgumentException.class, settings::validate);
      settings
          .getAuthentication()
          .setSecretReferences(
              Map.of(
                  "username",
                  "secret://env/USER_REF",
                  "password",
                  "credential://provider-password"));
      settings.validate();
      assertThrows(
          IllegalArgumentException.class, () -> new ApplePlatformStrategy().options(settings));
    }

    @Test
    @DisplayName("merges base named and authorized job values while preserving explicit false")
    void precedence() {
      var base = valid();
      base.setAutoAcceptAlerts(true);
      var named = new AppleControllerSettings();
      named.setDeviceName("named");
      named.setAutoAcceptAlerts(false);
      var job = new AppleControllerSettings();
      job.setDeviceName("job");
      var result = AppleControllerSettings.resolve(base, named, job);
      assertThat(result.getDeviceName()).isEqualTo("job");
      assertThat(result.getAutoAcceptAlerts()).isFalse();
      assertThat(result.getBundleId()).isEqualTo("com.example.fixture");
      assertThat(base.getDeviceName()).isEqualTo("fixture");
      assertThat(named.getDeviceName()).isEqualTo("named");
    }

    @Test
    @DisplayName("copies nested provider types and rejects conflicting ownership")
    void json() {
      var list = new ArrayList<Object>(List.of(3, true));
      var nested = new LinkedHashMap<String, Object>();
      nested.put("items", list);
      nested.put("nullable", null);
      var base = valid();
      base.setProviderOptions(Map.of("cloud:options", nested));
      list.clear();
      nested.clear();
      var result = AppleControllerSettings.resolve(base, null, null);
      assertThat(
              ((Map<?, ?>) result.getProviderOptions().get("cloud:options"))
                  .containsKey("nullable"))
          .isTrue();
      assertThat(((Map<?, ?>) result.getProviderOptions().get("cloud:options")).get("items"))
          .isEqualTo(List.of(3, true));
      assertThrows(
          UnsupportedOperationException.class, () -> result.getProviderOptions().put("x:y", true));
      var named = new AppleControllerSettings();
      named.setProviderOptions(Map.of("cloud:options", Map.of("other", false)));
      var combined = AppleControllerSettings.resolve(base, named, null);
      assertThat(((Map<?, ?>) combined.getProviderOptions().get("cloud:options")).get("other"))
          .isEqualTo(false);
      named.setProviderOptions(Map.of("cloud:options", Map.of("items", List.of(4))));
      assertThrows(
          IllegalArgumentException.class, () -> AppleControllerSettings.resolve(base, named, null));
    }

    @Test
    @DisplayName("rejects plaintext credentials and unsupported JSON without echoing values")
    void secrets() {
      var settings = valid();
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  settings.setProviderOptions(
                      Map.of("cloud:options", Map.of("accessToken", "CANARY"))));
      assertThat(failure).hasMessageNotContaining("CANARY");
      assertThrows(
          IllegalArgumentException.class,
          () -> settings.setProviderOptions(Map.of("cloud:options", new Object())));
      assertThrows(
          IllegalArgumentException.class,
          () -> settings.getAuthentication().setSecretReferences(Map.of("password", "CANARY")));
    }

    @Test
    @DisplayName("inherits nested WDA fields and validates configured ownership")
    void wda() {
      var base = valid();
      base.getWda().setLocalPort(8101);
      var named = new AppleControllerSettings();
      named.getWda().setMjpegPort(9101);
      var result = AppleControllerSettings.resolve(base, named, null);
      var options = new ApplePlatformStrategy().options(result);
      assertThat(options.getCapability("appium:wdaLocalPort")).isEqualTo(8101);
      assertThat(options.getCapability("appium:mjpegServerPort")).isEqualTo(9101);
      named.getWda().setMjpegPort(8101);
      assertThrows(
          IllegalArgumentException.class, () -> AppleControllerSettings.resolve(base, named, null));
    }
  }
}
