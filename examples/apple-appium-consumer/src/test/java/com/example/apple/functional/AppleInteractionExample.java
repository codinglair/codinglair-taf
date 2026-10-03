package com.example.apple.functional;

import com.codinglair.taf.mobile.appium.configuration.AppleProperties;
import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import com.example.apple.configuration.InteractionProperties;
import com.example.apple.configuration.MobileConfiguration;
import com.example.apple.screen.apple.AppleScreen;
import com.example.apple.task.InteractionTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

@ActiveProfiles("taf-local")
@ContextConfiguration(classes = MobileConfiguration.class, initializers = MobileConfiguration.Initializer.class)
public final class AppleInteractionExample extends TafBaseTest {
  @Autowired private InteractionProperties interaction;
  @Autowired private AppleProperties apple;

  @Test
  public void selectedAppleInteractionHasExpectedResult() {
    InteractionTask.verify(new AppleScreen(controller(AppleController.class, "primary"), interaction),
        apple.settings("primary").getExecutionMode());
  }
}
