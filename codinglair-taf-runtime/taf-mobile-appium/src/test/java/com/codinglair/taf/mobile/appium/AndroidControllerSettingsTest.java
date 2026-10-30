package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codinglair.taf.mobile.ApplicationMode;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Android controller configuration")
class AndroidControllerSettingsTest {
  @Nested
  @DisplayName("Validation")
  class Validation {
    @Test
    @DisplayName("leaves remote ADB connectivity unset by default")
    void remoteAdbDefaultsAreAbsent() {
      var settings = valid();

      assertThat(settings.getRemoteAdbHost()).isNull();
      assertThat(settings.getAdbPort()).isNull();
      settings.validate("taf.mobile.android");
    }

    @Test
    @DisplayName("rejects non-HTTP Appium endpoints")
    void rejectsUnsafeEndpoint() {
      var settings = valid();
      settings.setServerUrl(URI.create("file:///tmp/appium"));
      assertThatThrownBy(() -> settings.validate("taf.mobile.android"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("server-url");
    }

    @Test
    @DisplayName("rejects credentials embedded in the Appium endpoint")
    void rejectsEmbeddedCredentials() {
      var settings = valid();
      settings.setServerUrl(URI.create("http://user:secret@127.0.0.1:4723"));
      assertThatThrownBy(() -> settings.validate("taf.mobile.android"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("without embedded credentials");
    }

    @Test
    @DisplayName("requires an application path for packaged mode")
    void requiresPackagedApplication() {
      var settings = valid();
      settings.setApplicationMode(ApplicationMode.PACKAGED);
      assertThatThrownBy(() -> settings.validate("taf.mobile.android"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(".app");
    }

    @Test
    @DisplayName("requires explicit visual-artifact authorization for video")
    void requiresVideoAuthorization() {
      var settings = valid();
      settings.setVideo(true);
      assertThatThrownBy(() -> settings.validate("taf.mobile.android"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("allow-visual-artifacts");
    }

    @Test
    @DisplayName("rejects a blank remote ADB host")
    void rejectsBlankRemoteAdbHost() {
      var settings = valid();
      settings.setRemoteAdbHost("  ");

      assertThatThrownBy(() -> settings.validate("taf.mobile.android"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("remote-adb-host");
    }

    @Test
    @DisplayName("rejects remote ADB ports outside the TCP port range")
    void rejectsOutOfRangeAdbPort() {
      var settings = valid();

      settings.setAdbPort(0);
      assertThatThrownBy(() -> settings.validate("taf.mobile.android"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("adb-port");

      settings.setAdbPort(65536);
      assertThatThrownBy(() -> settings.validate("taf.mobile.android"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("adb-port");
    }
  }

  static AndroidControllerSettings valid() {
    var value = new AndroidControllerSettings();
    value.setServerUrl(URI.create("http://127.0.0.1:4723"));
    value.setDeviceName("emulator");
    value.setAppPackage("com.example.app");
    return value;
  }
}
