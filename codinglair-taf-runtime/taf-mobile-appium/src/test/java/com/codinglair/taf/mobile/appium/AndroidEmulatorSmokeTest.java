package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Opt-in Android environment smoke")
class AndroidEmulatorSmokeTest {
  @Test
  @DisplayName(
      "opens and cleans an Appium UiAutomator2 session when environment settings are supplied")
  void opensConfiguredSession() {
    assumeTrue(
        Boolean.getBoolean("taf.mobile.android.smoke")
            || Boolean.getBoolean("taf.mobile.android.device-smoke"));
    String endpoint = System.getenv("TAF_ANDROID_APPIUM_URL");
    String device = System.getenv("TAF_ANDROID_DEVICE_NAME");
    String appPackage = System.getenv("TAF_ANDROID_APP_PACKAGE");
    assumeTrue(
        endpoint != null && device != null && appPackage != null,
        "Appium smoke environment is not configured");
    URI serverUri = URI.create(endpoint);
    assumeTrue(appiumAvailable(serverUri), "Configured Appium endpoint is unavailable");
    var settings = new AndroidControllerSettings();
    settings.setServerUrl(serverUri);
    settings.setDeviceName(device);
    settings.setDeviceId(System.getenv("TAF_ANDROID_DEVICE_ID"));
    settings.setAppPackage(appPackage);
    settings.setAppActivity(System.getenv("TAF_ANDROID_APP_ACTIVITY"));
    settings.setAllowVisualArtifacts(false);
    var controller =
        new DefaultAndroidController("smoke", settings, AppiumAndroidSessionFactory.standard());
    try {
      controller.initialize(TestContexts.context());
      assertThat(controller.health().status())
          .isEqualTo(com.codinglair.taf.runtime.core.controller.HealthResult.Status.HEALTHY);
      assertThat(controller.pageSource()).isNotBlank();
    } finally {
      controller.close();
    }
  }

  private static boolean appiumAvailable(URI serverUri) {
    try {
      URI statusUri = serverUri.resolve(appendStatusPath(serverUri.getPath()));
      var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      var request = HttpRequest.newBuilder(statusUri).timeout(Duration.ofSeconds(2)).GET().build();
      int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      return status >= 200 && status < 300;
    } catch (java.io.IOException failure) {
      return false;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String appendStatusPath(String path) {
    String normalized = path == null || path.isBlank() ? "/" : path;
    return (normalized.endsWith("/") ? normalized : normalized + "/") + "status";
  }
}
