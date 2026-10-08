package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codinglair.taf.mobile.MobileTopology;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.service.AppleReadiness;
import com.codinglair.taf.mobile.appium.service.AppleResourceReservations;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Passive Apple topology readiness and observable reservation ownership")
class AppleReadinessTest {
  @Test
  @DisplayName("remote prerequisites stay unknown without requiring worker Xcode or optional video")
  void remote() {
    var settings = AppleConfigurationTest.valid();
    settings.setPrerequisites(Map.of("endpoint", true));
    var result = AppleReadiness.inspect(settings);
    assertThat(result.status()).isEqualTo(HealthResult.Status.UNKNOWN);
    assertThat(result.diagnostics())
        .containsKeys("target", "application", "wda", "compatibility")
        .doesNotContainKeys("endpoint", "xcode", "video");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "endpoint",
        "compatibility",
        "target",
        "application",
        "wda",
        "doctor",
        "xcode",
        "device",
        "signing"
      })
  @DisplayName("aggregates actionable missing local prerequisites without raw operator payloads")
  void negative(String dimension) {
    var settings = AppleConfigurationTest.valid();
    settings.setTopology(MobileTopology.LOCAL_HOST);
    settings.setPrerequisites(Map.of(dimension, false));
    var result = AppleReadiness.inspect(settings);
    assertThat(result.status()).isEqualTo(HealthResult.Status.UNAVAILABLE);
    assertThat(result.diagnostics().get(dimension)).contains("repair", dimension);
    assertThrows(
        IllegalArgumentException.class, () -> settings.setPrerequisites(Map.of("CANARY", true)));
    assertThat(result.toString()).doesNotContain("CANARY");
  }

  @Test
  @DisplayName(
      "observable collisions reject before ownership is acquired and release is idempotent")
  void reservations() throws Exception {
    var settings = AppleConfigurationTest.valid();
    settings.setDeviceId("owned-target");
    var coordinator = new AppleResourceReservations();
    var lease = coordinator.acquire(settings);
    assertThrows(IllegalStateException.class, () -> coordinator.acquire(settings));
    lease.close();
    var next = coordinator.acquire(settings);
    lease.close();
    assertThrows(IllegalStateException.class, () -> coordinator.acquire(settings));
    next.close();
  }

  @Test
  @DisplayName("WDA and MJPEG share the host port namespace across endpoint paths")
  void ports() throws Exception {
    var first = AppleConfigurationTest.valid();
    first.getWda().setLocalPort(8100);
    var second = AppleConfigurationTest.valid();
    second.getWda().setMjpegPort(8100);
    second.setServerUrl(first.getServerUrl().resolve("/different"));
    var coordinator = new AppleResourceReservations();
    try (var lease = coordinator.acquire(first)) {
      assertThrows(IllegalStateException.class, () -> coordinator.acquire(second));
    }
  }

  @Test
  @DisplayName(
      "positive declarations remain unknown until initialization and metadata snapshots are detached")
  void declarations() {
    var settings = AppleConfigurationTest.valid();
    settings.setPrerequisites(
        Map.of(
            "endpoint",
            true,
            "compatibility",
            true,
            "target",
            true,
            "application",
            true,
            "wda",
            true));
    var snapshot = AppleControllerSettings.merge(settings, null, null);
    settings.setPrerequisites(Map.of("target", false));
    assertThat(AppleReadiness.inspect(snapshot).status()).isEqualTo(HealthResult.Status.UNKNOWN);
    assertThat(AppleReadiness.inspect(snapshot).diagnostics()).containsOnlyKeys("session");
    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshot.getPrerequisites().put("target", false));
    assertThat(AppleReadiness.inspect(settings).status())
        .isEqualTo(HealthResult.Status.UNAVAILABLE);
  }

  @Test
  @DisplayName("provider maps cannot enable shared session override even under nested namespaces")
  void override() {
    var settings = AppleConfigurationTest.valid();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            settings.setProviderOptions(
                Map.of("provider:options", Map.of("session-override", true))));
  }
}
