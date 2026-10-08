package com.example.apple;

import com.codinglair.taf.runtime.core.TestSession;
import org.testng.annotations.Test;

public final class BlueprintPreflightTest {
  @Test
  public void createsAndClosesAnIsolatedTestSession() {
    try (TestSession session = TestSession.create()) {
      if (session == null) {
        throw new AssertionError("TestSession was not created");
      }
    }
  }
}
