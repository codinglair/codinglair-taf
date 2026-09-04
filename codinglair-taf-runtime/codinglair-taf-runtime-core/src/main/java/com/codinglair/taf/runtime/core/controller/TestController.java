package com.codinglair.taf.runtime.core.controller;

import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import java.util.stream.Stream;

/** Technology-neutral replacement controller lifecycle defined by ADR-003. */
public interface TestController extends AutoCloseable {
  ControllerIdentity identity();

  ControllerState state();

  void initialize(ControllerContext context);

  HealthResult health();

  Stream<TestArtifact> collectArtifacts(ArtifactReason reason);

  @Override
  void close();
}
