package com.codinglair.taf.runtime.environment;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

final class TestRequests {
  private TestRequests() {}

  static EnvironmentRequest external(String name) {
    return new EnvironmentRequest(
        name,
        new EnvironmentType("database"),
        EnvironmentMode.EXTERNAL,
        Set.of(),
        Map.of(),
        Duration.ofSeconds(1));
  }
}
