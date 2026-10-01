package com.codinglair.taf.mobile.appium;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mobile.MobilePlatform;
import com.codinglair.taf.mobile.MobileScreen;
import com.codinglair.taf.mobile.appium.platform.AppleLocator;
import com.codinglair.taf.mobile.appium.platform.AppleLocator.Kind;
import com.codinglair.taf.mobile.appium.service.AppleController;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** Compiled representative consumer composition; business screens are not runtime library APIs. */
@DisplayName("Shared checkout task with session-local platform screens")
class SharedCheckoutTaskTest extends AppleProtocolFixture {
  interface CheckoutScreen {
    void submit();
  }

  record SubmitOrder(CheckoutScreen screen) {
    SubmitOrder {
      Objects.requireNonNull(screen);
    }

    void execute() {
      screen.submit();
    }
  }

  static final class AppleCheckoutScreen implements CheckoutScreen, MobileScreen {
    private static final AppleLocator SUBMIT = new AppleLocator(Kind.ACCESSIBILITY, "submit-order");
    private final AppleController controller;

    AppleCheckoutScreen(AppleController controller) {
      this.controller = controller;
    }

    @Override
    public MobilePlatform platform() {
      return MobilePlatform.IOS;
    }

    @Override
    public void submit() {
      controller.tap(controller.find(SUBMIT));
    }
  }

  static final class AndroidCheckoutScreen implements CheckoutScreen, MobileScreen {
    private static final String SUBMIT = "new UiSelector().resourceId(\"fixture:id/submit\")";
    private final AndroidController controller;

    AndroidCheckoutScreen(AndroidController controller) {
      this.controller = controller;
    }

    @Override
    public MobilePlatform platform() {
      return MobilePlatform.ANDROID;
    }

    @Override
    public void submit() {
      controller.tap(controller.find(SUBMIT));
    }
  }

  static final class WebCheckoutScreen implements CheckoutScreen {
    private static final By SUBMIT = By.cssSelector("[data-test='submit-order']");
    private final WebDriver driver;

    WebCheckoutScreen(WebDriver driver) {
      this.driver = driver;
    }

    @Override
    public void submit() {
      driver.findElement(SUBMIT).click();
    }
  }

  static CheckoutScreen mobileScreen(
      AppleCheckoutScreen apple, AndroidCheckoutScreen android, MobilePlatform platform) {
    return switch (platform) {
      case IOS -> apple;
      case ANDROID -> android;
    };
  }

  @Test
  @DisplayName(
      "executes the same task through Apple, Android and web screens with distinct locators")
  void sharedTask() {
    initialize(settings());
    var appleScreen = new AppleCheckoutScreen(controller);
    var androidSettings = new AndroidControllerSettings();
    androidSettings.setServerUrl(settings().getServerUrl());
    androidSettings.setDeviceName("fixture");
    androidSettings.setAppPackage("com.example.fixture");
    androidSettings.setTerminateAppOnClose(false);
    var android =
        new DefaultAndroidController(
            "android", androidSettings, AppiumAndroidSessionFactory.standard());
    android.initialize(TestContexts.context());
    try {
      var androidScreen = new AndroidCheckoutScreen(android);
      new SubmitOrder(mobileScreen(appleScreen, androidScreen, MobilePlatform.IOS)).execute();
      new SubmitOrder(mobileScreen(appleScreen, androidScreen, MobilePlatform.ANDROID)).execute();
      context = "WEBVIEW_FIXTURE";
      new SubmitOrder(new WebCheckoutScreen(controller.nativeDriver())).execute();
      assertThat(String.join("\n", requests))
          .contains(
              "accessibility id",
              "submit-order",
              "-android uiautomator",
              "fixture:id/submit",
              "css selector",
              "data-test");
      assertThat(requests.stream().filter(r -> r.contains("/click")).count()).isEqualTo(3);
    } finally {
      android.close();
    }
  }
}
