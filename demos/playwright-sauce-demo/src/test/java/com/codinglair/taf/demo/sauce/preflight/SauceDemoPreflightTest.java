package com.codinglair.taf.demo.sauce.preflight;

import com.codinglair.taf.core.annotation.reporting.TestCaseId;
import com.codinglair.taf.runtime.testng.TafBaseTest;
import org.testng.annotations.Test;

/** Deterministic default-suite proof of the framework-owned TestNG session lifecycle. */
public final class SauceDemoPreflightTest extends TafBaseTest {
  @Test
  @TestCaseId("SAUCE-DEMO-PREFLIGHT-001")
  public void createsAnIsolatedRuntimeSessionWithoutOpeningExternalResources() {
    if (testSession() == null) {
      throw new AssertionError("TafBaseTest did not create the invocation session");
    }
  }
}
