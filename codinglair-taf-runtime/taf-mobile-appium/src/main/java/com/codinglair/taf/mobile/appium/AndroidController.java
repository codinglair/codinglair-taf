package com.codinglair.taf.mobile.appium;

import com.codinglair.taf.mobile.MobileController;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.WebElement;

public interface AndroidController extends MobileController<AndroidDriver, WebElement> {
  @Override
  AndroidDriver nativeDriver();

  void openNotifications();
}
