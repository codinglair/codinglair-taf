package com.codinglair.taf.mobile.appium.platform;

import com.codinglair.taf.mobile.appium.AndroidControllerSettings;
import com.codinglair.taf.runtime.core.security.ResourceAccess;
import com.codinglair.taf.runtime.core.security.ResourceAuthorizer;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.remote.http.HttpClient;

/** Existing Android construction behavior, isolated from Apple option selection. */
public final class AndroidPlatformStrategy {
  public AndroidDriver create(AndroidControllerSettings settings) throws Exception {
    return create(settings, null);
  }

  /**
   * A supplied governed policy is enforced before each exchange; trusted legacy use is unchanged.
   */
  public AndroidDriver create(AndroidControllerSettings settings, ResourceAuthorizer authorizer)
      throws Exception {
    if (authorizer != null) authorize(settings, authorizer, "connect");
    var options =
        new UiAutomator2Options()
            .setDeviceName(settings.getDeviceName())
            .setAppPackage(settings.getAppPackage())
            .setNewCommandTimeout(settings.getCommandTimeout());
    if (settings.getDeviceId() != null && !settings.getDeviceId().isBlank())
      options.setUdid(settings.getDeviceId());
    if (settings.getAppActivity() != null && !settings.getAppActivity().isBlank())
      options.setAppActivity(settings.getAppActivity());
    if (settings.getApp() != null)
      options.setApp(settings.getApp().toAbsolutePath().normalize().toString());
    if (authorizer == null) return new AndroidDriver(settings.getServerUrl().toURL(), options);
    return new AndroidDriver(
        settings.getServerUrl().toURL(),
        config ->
            HttpClient.Factory.createDefault()
                .createClient(
                    config.withFilter(
                        next ->
                            request -> {
                              authorize(settings, authorizer, "execute");
                              return next.execute(request);
                            })),
        options);
  }

  private static void authorize(
      AndroidControllerSettings settings, ResourceAuthorizer authorizer, String action) {
    authorizer.require(
        new ResourceAccess(
            ResourceAccess.Kind.ENDPOINT, settings.getServerUrl().toASCIIString(), action));
    authorizer.require(
        new ResourceAccess(
            ResourceAccess.Kind.TARGET,
            settings.getDeviceId() == null || settings.getDeviceId().isBlank()
                ? "device-name:" + settings.getDeviceName()
                : settings.getDeviceId(),
            "select"));
    authorizer.require(
        new ResourceAccess(ResourceAccess.Kind.APPLICATION, settings.getAppPackage(), action));
    if (settings.getApp() != null)
      authorizer.require(
          new ResourceAccess(
              ResourceAccess.Kind.APPLICATION,
              settings.getApp().toAbsolutePath().normalize().toString(),
              action));
  }
}
