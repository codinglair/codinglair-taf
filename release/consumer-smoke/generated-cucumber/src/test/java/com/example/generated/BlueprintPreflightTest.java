package com.example.generated;

import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import org.testng.annotations.Test;

public final class BlueprintPreflightTest extends com.codinglair.taf.runtime.testng.TafBaseTest {
  @Test
  @TestCaseId("GENERATED-PREFLIGHT-001")
  public void createsAndClosesAnIsolatedTestSession() {
    if (testSession() == null) {
      throw new AssertionError("TafBaseTest did not create the invocation session");
    }
  }
}

