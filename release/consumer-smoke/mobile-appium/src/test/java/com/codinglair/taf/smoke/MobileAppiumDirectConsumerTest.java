package com.codinglair.taf.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mobile.MobileDeviceFamily;
import com.codinglair.taf.mobile.MobileDeviceKind;
import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.MobileTopology;
import com.codinglair.taf.mobile.appium.AndroidControllerSettings;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.platform.ApplePlatformStrategy;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("External direct Appium module")
class MobileAppiumDirectConsumerTest {
  @Nested
  @DisplayName("Apple selections")
  class AppleSelections {
    @ParameterizedTest
    @MethodSource("com.codinglair.taf.smoke.MobileAppiumDirectConsumerTest#appleSelections")
    @DisplayName("loads every family, kind, mode, and topology through public contracts")
    void loads(
        MobileDeviceFamily family,
        MobileDeviceKind kind,
        MobileExecutionMode mode,
        MobileTopology topology) {
      var settings = new AppleControllerSettings();
      settings.setPlatform(family == MobileDeviceFamily.IPHONE ? "ios" : "ipados");
      settings.setDeviceKind(kind);
      settings.setDeviceName("consumer-target");
      settings.setDeviceId("authorized-consumer-target");
      settings.setExecutionMode(mode);
      settings.setTopology(topology);
      settings.setServerUrl(URI.create("http://127.0.0.1:4723/custom/wd/hub"));
      if (mode != MobileExecutionMode.SAFARI) settings.setBundleId("com.example.consumer");

      var options = new ApplePlatformStrategy().options(settings);
      assertThat(settings.family()).isEqualTo(family);
      assertThat(options.getCapability("appium:automationName")).isEqualTo("XCUITest");
    }
  }

  @Nested
  @DisplayName("Android compatibility")
  class AndroidCompatibility {
    @org.junit.jupiter.api.Test
    @DisplayName("retains the public Android defaults and validation contract")
    void retainsDefaults() {
      var settings = new AndroidControllerSettings();
      settings.setServerUrl(URI.create("http://127.0.0.1:4723"));
      settings.setDeviceName("legacy-android");
      settings.setAppPackage("com.example.android");
      settings.validate("taf.mobile.android.controllers.legacy");

      assertThat(settings.getCommandTimeout()).isEqualTo(java.time.Duration.ofMinutes(2));
      assertThat(settings.isTerminateAppOnClose()).isTrue();
    }
  }

  static Stream<Arguments> appleSelections() {
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
                                            .map(topology -> Arguments.of(family, kind, mode, topology)))));
  }
}
