package com.codinglair.taf.demo.sauce.context;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.web.playwright.PlaywrightController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = SauceDemoApplication.class,
    properties = "spring.main.web-application-type=none")
class ContextLoadingTest {
  @Autowired TestSessionFactory sessions;

  @org.junit.jupiter.api.Test
  void selectedControllerIsRegisteredWithoutBrowserStartup() {
    try (com.codinglair.taf.runtime.core.TestSession session = sessions.create()) {
      assertTrue(session.getControllerRegistry().hasController(PlaywrightController.class, "shop"));
    }
  }
}
