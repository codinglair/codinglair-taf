package __BASE_PACKAGE__.architecture;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import __BASE_PACKAGE__.PlaywrightConsumerApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = PlaywrightConsumerApplication.class)
class LifecycleTest {
  @Autowired TestSessionFactory sessions;

  @org.junit.jupiter.api.Test
  void sessionsAreIsolatedAndCleanupIsIdempotent() {
    com.codinglair.taf.runtime.core.TestSession first = sessions.create();
    com.codinglair.taf.runtime.core.TestSession second = sessions.create();
    assertNotEquals(first.getSessionId(), second.getSessionId());
    first.close();
    first.close();
    second.close();
  }
}
