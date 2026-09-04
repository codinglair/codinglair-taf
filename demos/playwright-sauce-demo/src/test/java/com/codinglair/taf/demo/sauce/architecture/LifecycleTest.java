package com.codinglair.taf.demo.sauce.architecture;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.codinglair.taf.demo.sauce.SauceDemoApplication;
import com.codinglair.taf.runtime.core.TestSession;
import com.codinglair.taf.runtime.core.lifecycle.TestSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = SauceDemoApplication.class)
class LifecycleTest {
  @Autowired TestSessionFactory sessions;

  @org.junit.jupiter.api.Test
  void sessionsAreIsolatedAndCleanupIsIdempotent() {
    TestSession first = sessions.create();
    TestSession second = sessions.create();
    assertNotEquals(first.getSessionId(), second.getSessionId());
    first.close();
    first.close();
    second.close();
  }
}
