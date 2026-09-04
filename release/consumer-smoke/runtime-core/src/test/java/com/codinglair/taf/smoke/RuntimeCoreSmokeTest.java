package com.codinglair.taf.smoke;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.codinglair.taf.runtime.core.TestSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Runtime core staged consumer")
class RuntimeCoreSmokeTest {
  @Test
  @DisplayName("creates and closes an isolated session")
  void createsAndClosesAnIsolatedSession() {
    try (TestSession session = TestSession.create()) {
      assertFalse(session.getSessionId().isBlank());
    }
  }
}
