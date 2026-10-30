package com.codinglair.taf.mobile.appium.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mobile.appium.AndroidControllerSettings;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Android UiAutomator2 capability construction")
class AndroidPlatformStrategyTest {
  @Test
  @DisplayName("emits both explicitly configured remote ADB capabilities")
  void emitsBothRemoteAdbCapabilities() {
    var settings = settings();
    settings.setRemoteAdbHost("host.docker.internal");
    settings.setAdbPort(5037);

    var capabilities = AndroidPlatformStrategy.options(settings);

    assertThat(capabilities.getCapability("appium:remoteAdbHost"))
        .isEqualTo("host.docker.internal");
    assertThat(capabilities.getCapability("appium:adbPort")).isEqualTo(5037);
  }

  @Test
  @DisplayName("emits only an explicitly configured remote ADB host")
  void emitsOnlyRemoteAdbHost() {
    var settings = settings();
    settings.setRemoteAdbHost("adb.example.test");

    var capabilities = AndroidPlatformStrategy.options(settings);

    assertThat(capabilities.getCapability("appium:remoteAdbHost")).isEqualTo("adb.example.test");
    assertThat(capabilities.getCapability("appium:adbPort")).isNull();
  }

  @Test
  @DisplayName("emits only an explicitly configured ADB port")
  void emitsOnlyAdbPort() {
    var settings = settings();
    settings.setAdbPort(5038);

    var capabilities = AndroidPlatformStrategy.options(settings);

    assertThat(capabilities.getCapability("appium:remoteAdbHost")).isNull();
    assertThat(capabilities.getCapability("appium:adbPort")).isEqualTo(5038);
  }

  @Test
  @DisplayName("omits remote ADB capabilities while preserving existing Android capabilities")
  void omitsRemoteAdbCapabilitiesAndPreservesExistingCapabilities() {
    var settings = settings();

    var capabilities = AndroidPlatformStrategy.options(settings);

    assertThat(capabilities.getCapability("appium:remoteAdbHost")).isNull();
    assertThat(capabilities.getCapability("appium:adbPort")).isNull();
    assertThat(capabilities.getCapability("appium:udid")).isEqualTo("emulator-5554");
    assertThat(capabilities.getCapability("appium:deviceName")).isEqualTo("Android emulator");
    assertThat(capabilities.getCapability("appium:appPackage")).isEqualTo("com.example.app");
    assertThat(capabilities.getCapability("appium:appActivity")).isEqualTo(".MainActivity");
    assertThat(capabilities.getCapability("appium:newCommandTimeout")).isEqualTo(90L);
  }

  private static AndroidControllerSettings settings() {
    var settings = new AndroidControllerSettings();
    settings.setServerUrl(URI.create("http://127.0.0.1:4723"));
    settings.setDeviceName("Android emulator");
    settings.setDeviceId("emulator-5554");
    settings.setAppPackage("com.example.app");
    settings.setAppActivity(".MainActivity");
    settings.setCommandTimeout(Duration.ofSeconds(90));
    return settings;
  }
}
