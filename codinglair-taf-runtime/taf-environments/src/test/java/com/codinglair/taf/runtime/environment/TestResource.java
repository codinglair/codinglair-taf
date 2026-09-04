package com.codinglair.taf.runtime.environment;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class TestResource implements EnvironmentResource {
  private final String id;
  private final EnvironmentMode mode;
  private final AtomicInteger cleanupCalls = new AtomicInteger();

  TestResource(String id, EnvironmentMode mode) {
    this.id = id;
    this.mode = mode;
  }

  public String id() {
    return id;
  }

  public EnvironmentType type() {
    return new EnvironmentType("database");
  }

  public EnvironmentMode mode() {
    return mode;
  }

  public Map<String, String> properties() {
    return Map.of("endpoint", "localhost:1234");
  }

  public EnvironmentDiagnostic diagnose() {
    return new EnvironmentDiagnostic(EnvironmentStatus.READY, "Ready", "", Map.of(), Instant.now());
  }

  public void cleanup() {
    cleanupCalls.incrementAndGet();
  }

  int cleanupCalls() {
    return cleanupCalls.get();
  }
}
