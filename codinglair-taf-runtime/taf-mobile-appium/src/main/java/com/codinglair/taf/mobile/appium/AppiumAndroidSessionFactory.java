package com.codinglair.taf.mobile.appium;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

@FunctionalInterface
interface AppiumAndroidSessionFactory {
  AndroidDriver create(AndroidControllerSettings settings) throws Exception;

  static AppiumAndroidSessionFactory standard() {
    return settings -> {
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
      return new AndroidDriver(settings.getServerUrl().toURL(), options);
    };
  }
}
