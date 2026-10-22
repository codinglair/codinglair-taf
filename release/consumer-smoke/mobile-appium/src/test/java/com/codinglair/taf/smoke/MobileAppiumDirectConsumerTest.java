package com.codinglair.taf.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.mobile.MobileDeviceFamily;
import com.codinglair.taf.mobile.MobileDeviceKind;
import com.codinglair.taf.mobile.MobileExecutionMode;
import com.codinglair.taf.mobile.MobileTopology;
import com.codinglair.taf.mobile.appium.AndroidControllerSettings;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.platform.ApplePlatformStrategy;
import java.net.URI;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

class MobileAppiumDirectConsumerTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
  @Test(dataProvider = "appleSelections")
  void loads(
        MobileDeviceFamily family,
        MobileDeviceKind kind,
        MobileExecutionMode mode,
        MobileTopology topology) {
      var settings = new AppleControllerSettings();
      settings.setPlatform(family == MobileDeviceFamily.IPHONE ? "ios" : "ipados");
      settings.setDeviceKind(kind);
      settings.setDeviceName("consumer-target");
      settings.setDeviceId("authorized-consumer-target");
      settings.setExecutionMode(mode);
      settings.setTopology(topology);
      settings.setServerUrl(URI.create("http://127.0.0.1:4723/custom/wd/hub"));
      if (mode != MobileExecutionMode.SAFARI) settings.setBundleId("com.example.consumer");

      var options = new ApplePlatformStrategy().options(settings);
      assertThat(settings.family()).isEqualTo(family);
      assertThat(options.getCapability("appium:automationName")).isEqualTo("XCUITest");
  }

  @Test
  void retainsDefaults() {
      var settings = new AndroidControllerSettings();
      settings.setServerUrl(URI.create("http://127.0.0.1:4723"));
      settings.setDeviceName("legacy-android");
      settings.setAppPackage("com.example.android");
      settings.validate("taf.mobile.android.controllers.legacy");

      assertThat(settings.getCommandTimeout()).isEqualTo(java.time.Duration.ofMinutes(2));
      assertThat(settings.isTerminateAppOnClose()).isTrue();
  }

  @DataProvider
  Object[][] appleSelections() {
    var values = new java.util.ArrayList<Object[]>();
    for (var family : MobileDeviceFamily.values())
      for (var kind : MobileDeviceKind.values())
        for (var mode : MobileExecutionMode.values())
          for (var topology : MobileTopology.values())
            values.add(new Object[] {family, kind, mode, topology});
    return values.toArray(Object[][]::new);
  }
}
