package com.codinglair.taf.runtime.core.controller;

import com.codinglair.taf.runtime.core.reporting.ArtifactCollector;
import java.util.Objects;

/** Neutral services available while a controller is initialized. */
public record ControllerContext(
    String sessionId, EnvironmentAccess environments, ArtifactCollector artifacts) {
  public ControllerContext {
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(environments, "environments");
    Objects.requireNonNull(artifacts, "artifacts");
  }
}
