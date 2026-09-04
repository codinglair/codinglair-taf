package com.codinglair.taf.mobile.appium;

import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;
import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import com.codinglair.taf.runtime.core.reporting.abstraction.TafTest;

final class TestContexts {
  private TestContexts() {}

  static ControllerContext context() {
    EnvironmentAccess environments = EnvironmentAccess.unavailable();
    ArtifactCollector artifacts =
        new ArtifactCollector(TafTest.of("test", "TestContexts"), "test", "test");
    return new ControllerContext("test", environments, artifacts);
  }
}
