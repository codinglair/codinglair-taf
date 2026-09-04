package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.EnvironmentRequest;
import java.util.Objects;

/** Consumer-supplied request that opts into a Spring-owned shared resource bean. */
public record SharedEnvironmentRequest(EnvironmentRequest request) {
  public SharedEnvironmentRequest {
    Objects.requireNonNull(request, "request");
  }
}
