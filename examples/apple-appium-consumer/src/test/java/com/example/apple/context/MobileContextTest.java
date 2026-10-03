package com.example.apple.context;

import com.codinglair.taf.mobile.appium.service.AppleController;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.runtime.core.preflight.ConsumerPreflight;
import com.example.apple.configuration.MobileConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testng.annotations.Test;

public final class MobileContextTest {
  @Test
  public void loadsWithoutAppleInfrastructureAndAggregatesMissingPrerequisites() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().setActiveProfiles("taf-local");
      new MobileConfiguration.Initializer().initialize(context);
      context.register(MobileConfiguration.class);
      context.refresh();
      try (var session = context.getBean(TestSessionFactory.class).create()) {
        if (!session.getControllerRegistry().hasController(AppleController.class, "primary"))
          throw new AssertionError("Named lazy Apple controller was not registered");
      }
      if (context.getBean(ConsumerPreflight.class).inspect().passed())
        throw new AssertionError("Unconfirmed execution prerequisites must fail preflight");
    }
  }
}
