package com.codinglair.taf.qualification.apple;

import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

@ActiveProfiles("taf-local")
@ContextConfiguration(
    classes = AppleSimulatorConfiguration.class,
    initializers = AppleSimulatorConfiguration.Initializer.class)
@TestPropertySource(
    properties = {
      "taf.mobile.apple.controllers.native.device-name=fixture",
      "taf.mobile.apple.controllers.native.device-id=fixture-native",
      "taf.mobile.apple.controllers.native.platform-version=18.5",
      "taf.mobile.apple.controllers.hybrid.device-name=fixture",
      "taf.mobile.apple.controllers.hybrid.device-id=fixture-hybrid",
      "taf.mobile.apple.controllers.hybrid.platform-version=18.5",
      "taf.mobile.apple.controllers.safari.device-name=fixture",
      "taf.mobile.apple.controllers.safari.device-id=fixture-safari",
      "taf.mobile.apple.controllers.safari.platform-version=18.5"
    })
public final class AppleSimulatorConfigurationTest extends AbstractTestNGSpringContextTests {
  @Autowired private TestSessionFactory sessions;

  @Test
  public void loadsEveryNamedControllerFromApplicationYaml() {
    try (var session = sessions.create()) {
      var controllers = session.getControllerRegistry();
      assertRegistered(controllers.hasController(AppleController.class, "native"), "native");
      assertRegistered(controllers.hasController(AppleController.class, "hybrid"), "hybrid");
      assertRegistered(controllers.hasController(AppleController.class, "safari"), "safari");
    }
  }

  private static void assertRegistered(boolean registered, String name) {
    if (!registered) throw new AssertionError("Apple controller was not registered: " + name);
  }
}
