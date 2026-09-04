package __BASE_PACKAGE__.context;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import com.codinglair.taf.web.playwright.PlaywrightController;
import __BASE_PACKAGE__.PlaywrightConsumerApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = PlaywrightConsumerApplication.class,
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
