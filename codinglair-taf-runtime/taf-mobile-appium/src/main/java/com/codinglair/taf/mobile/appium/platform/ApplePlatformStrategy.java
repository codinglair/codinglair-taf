package com.codinglair.taf.mobile.appium.platform;

import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.appium.configuration.AppleAuthentication.Mechanism;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import java.net.MalformedURLException;

/** XCUITest construction only; the caller owns the resulting session and its cleanup. */
public final class ApplePlatformStrategy {
  public XCUITestOptions options(AppleControllerSettings settings) {
    settings.validate();
    if (settings.getAuthentication().getMechanism() != Mechanism.NONE)
      throw new IllegalArgumentException(
          "Authenticated Apple transport requires the authorized provider integration from SEC-130-001");
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
    }
    options.setCapability("appium:autoAcceptAlerts", settings.getAutoAcceptAlerts());
    options.setCapability("appium:autoDismissAlerts", settings.getAutoDismissAlerts());
    settings.providerCapabilities().forEach(options::setCapability);
    settings.getWda().capabilities().forEach(options::setCapability);
    return options;
  }

  public IOSDriver create(AppleControllerSettings settings) {
    var options = options(settings);
    try {
      return new IOSDriver(settings.getServerUrl().toURL(), options);
    } catch (MalformedURLException failure) {
      throw new IllegalArgumentException("Apple endpoint is invalid", failure);
    }
  }
}
