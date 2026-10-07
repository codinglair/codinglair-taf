package com.codinglair.taf.mobile.appium.platform;

import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.service.AppleTransportSecurity;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import java.net.MalformedURLException;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;

/** XCUITest construction only; the caller owns the resulting session and its cleanup. */
public final class ApplePlatformStrategy {
  public XCUITestOptions options(AppleControllerSettings settings) {
    settings.validate();
    var options = new AppleXcuITestOptions();
    options.setCapability("platformName", "iOS");
    options.setCapability("appium:automationName", "XCUITest");
    options.setDeviceName(settings.getDeviceName());
    options.setNewCommandTimeout(settings.getCommandTimeout());
    // Reset is an explicit controller policy, never an implicit server-side device/app reset.
    options.setNoReset(true);
    if (settings.getDeviceId() != null) options.setUdid(settings.getDeviceId());
    if (settings.getPlatformVersion() != null)
      options.setPlatformVersion(settings.getPlatformVersion());
    if (settings.getExecutionMode() == MobileExecutionMode.SAFARI)
      options.setCapability("browserName", "Safari");
    else {
      if (settings.getAppReference() != null) options.setApp(settings.getAppReference().value());
      if (settings.getBundleId() != null) options.setBundleId(settings.getBundleId());
      if (!settings.getAdditionalWebviewBundleIds().isEmpty())
        options.setCapability(
            "appium:additionalWebviewBundleIds", settings.getAdditionalWebviewBundleIds());
    }
    options.setCapability("appium:autoAcceptAlerts", settings.getAutoAcceptAlerts());
    options.setCapability("appium:autoDismissAlerts", settings.getAutoDismissAlerts());
    settings.providerCapabilities().forEach(options::setCapability);
    settings.getWda().capabilities().forEach(options::setCapability);
    return options;
  }

  public IOSDriver create(AppleControllerSettings settings) {
    return create(settings, AppleTransportSecurity.trusted(settings, null), "standalone");
  }

  public IOSDriver create(
      AppleControllerSettings settings, AppleTransportSecurity security, String sessionId) {
    var options = options(settings);
    security.requireSettings(settings);
    try {
      return new IOSDriver(
          settings.getServerUrl().toURL(),
          config -> new AppleHttpClient(settings, security, sessionId),
          options);
    } catch (MalformedURLException failure) {
      throw new IllegalArgumentException("Apple endpoint is invalid", failure);
    }
  }

  /** Authenticated, bounded cleanup of a provider-identified owned session only. */
  public void cleanupOwned(
      AppleControllerSettings settings,
      String ownedSession,
      AppleTransportSecurity security,
      String sessionId) {
    if (!ownedSession.matches("[A-Za-z0-9_-]{1,128}"))
      throw new IllegalArgumentException("Invalid owned Apple session identifier");
    try (var client = new AppleHttpClient(settings, security, sessionId)) {
      int status =
          client
              .execute(new HttpRequest(HttpMethod.DELETE, "/session/" + ownedSession))
              .getStatus();
      if (status != 200 && status != 404)
        throw new IllegalStateException("Owned Apple session cleanup failed");
    }
  }
}
