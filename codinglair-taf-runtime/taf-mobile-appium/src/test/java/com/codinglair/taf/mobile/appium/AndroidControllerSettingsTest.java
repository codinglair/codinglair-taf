package com.codinglair.taf.mobile.appium;

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
  }

  static AndroidControllerSettings valid() {
    var value = new AndroidControllerSettings();
    value.setServerUrl(URI.create("http://127.0.0.1:4723"));
    value.setDeviceName("emulator");
    value.setAppPackage("com.example.app");
    return value;
  }
}
