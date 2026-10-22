package com.codinglair.taf.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.codinglair.taf.runtime.core.TestSession;
import org.testng.annotations.Test;
class RuntimeCoreSmokeTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
  @Test
  void createsAndClosesAnIsolatedSession() {
    try (TestSession session = TestSession.create()) {
      assertThat(session.getSessionId().isBlank()).isFalse();
    }
  }
}
