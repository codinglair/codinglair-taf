package com.codinglair.taf.mobile.appium.service;

import com.codinglair.taf.mobile.MobileDeviceFamily;
import com.codinglair.taf.mobile.MobilePlatform;
import com.codinglair.taf.runtime.core.controller.TestController;
import io.appium.java_client.ios.IOSDriver;

/** Apple session contract. Platform operations are delivered by MOB-130-003. */
public interface AppleController extends TestController {
  IOSDriver nativeDriver();

  MobileDeviceFamily family();

  default MobilePlatform platform() {
    return MobilePlatform.IOS;
  }
}
