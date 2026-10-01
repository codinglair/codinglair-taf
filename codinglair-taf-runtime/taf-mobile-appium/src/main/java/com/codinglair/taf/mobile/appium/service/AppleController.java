package com.codinglair.taf.mobile.appium.service;

import com.codinglair.taf.mobile.MobileDeviceFamily;
import com.codinglair.taf.mobile.MobileOrientation;
import com.codinglair.taf.mobile.MobilePlatform;
import com.codinglair.taf.mobile.appium.platform.AppleLocator;
import com.codinglair.taf.runtime.core.controller.TestController;
import io.appium.java_client.appmanagement.ApplicationState;
import io.appium.java_client.ios.IOSDriver;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Apple-specific operations; Android contracts remain independent. */
public interface AppleController extends TestController {
  IOSDriver nativeDriver();

  MobileDeviceFamily family();

  AppleElement find(AppleLocator locator);

  void tap(AppleElement element);

  void type(AppleElement element, String text);

  String text(AppleElement element);

  void swipe(int startX, int startY, int endX, int endY, Duration duration);

  void longPress(AppleElement element, Duration duration);

  void setOrientation(MobileOrientation orientation);

  void install();

  void uninstall();

  void launch();

  void activate();

  ApplicationState queryAppState();

  void terminate();

  void reset();

  void background(Duration duration);

  void openDeepLink(String uri);

  void grantPermission(String permission);

  void revokePermission(String permission);

  void acceptDialog();

  void dismissDialog();

  void openNotifications();

  Set<String> contexts();

  void selectWebView(String context);

  void returnToNative();

  void navigate(String uri);

  /** Explicit failure for Android-only extensions; never translates their semantics. */
  default void androidOperation(AndroidOperation operation) {
    throw new UnsupportedOperationException(
        "Android "
            + Objects.requireNonNull(operation)
            + " is unsupported on Apple; use an Apple screen or qualified XCUITest operation");
  }

  enum AndroidOperation {
    ACTIVITY,
    INTENT,
    KEY_CODE,
    NETWORK,
    LOGCAT
  }

  default MobilePlatform platform() {
    return MobilePlatform.IOS;
  }
}
