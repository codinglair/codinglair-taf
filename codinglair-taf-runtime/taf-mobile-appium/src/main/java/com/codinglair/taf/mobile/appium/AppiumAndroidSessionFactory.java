package com.codinglair.taf.mobile.appium;

import com.codinglair.taf.mobile.appium.platform.AndroidPlatformStrategy;
import io.appium.java_client.android.AndroidDriver;

@FunctionalInterface
interface AppiumAndroidSessionFactory {
  AndroidDriver create(AndroidControllerSettings settings) throws Exception;

  static AppiumAndroidSessionFactory standard() {
    return new AndroidPlatformStrategy()::create;
  }
}
