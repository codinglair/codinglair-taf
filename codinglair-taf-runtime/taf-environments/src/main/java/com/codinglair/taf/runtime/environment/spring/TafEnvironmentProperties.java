package com.codinglair.taf.runtime.environment.spring;

import com.codinglair.taf.runtime.environment.ContainerLifecycle;
import com.codinglair.taf.runtime.environment.EnvironmentMode;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed Spring composition settings. Core environment contracts remain Spring independent. */
@ConfigurationProperties("taf.environment")
public class TafEnvironmentProperties {
  private EnvironmentMode mode = EnvironmentMode.EXTERNAL;
  private boolean testcontainersEnabled;
  private ContainerLifecycle lifecycle = ContainerLifecycle.ISOLATED;
  private EnvironmentStartup startup = EnvironmentStartup.LAZY;
  private Duration readinessTimeout = Duration.ofSeconds(60);
  private boolean logCapture = true;
  private EnvironmentCleanupPolicy cleanup = EnvironmentCleanupPolicy.ALWAYS;

  public EnvironmentMode getMode() {
    return mode;
  }

  public void setMode(EnvironmentMode mode) {
    this.mode = Objects.requireNonNull(mode, "mode");
  }

  public boolean isTestcontainersEnabled() {
    return testcontainersEnabled;
  }

  public void setTestcontainersEnabled(boolean enabled) {
    this.testcontainersEnabled = enabled;
  }

  public ContainerLifecycle getLifecycle() {
    return lifecycle;
  }

  public void setLifecycle(ContainerLifecycle lifecycle) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  public EnvironmentStartup getStartup() {
    return startup;
  }

  public void setStartup(EnvironmentStartup startup) {
    this.startup = Objects.requireNonNull(startup, "startup");
  }

  public Duration getReadinessTimeout() {
    return readinessTimeout;
  }

  public void setReadinessTimeout(Duration timeout) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("taf.environment.readiness-timeout must be positive");
    }
    this.readinessTimeout = timeout;
  }

  public boolean isLogCapture() {
    return logCapture;
  }

  public void setLogCapture(boolean logCapture) {
    this.logCapture = logCapture;
  }

  public EnvironmentCleanupPolicy getCleanup() {
    return cleanup;
  }

  public void setCleanup(EnvironmentCleanupPolicy cleanup) {
    this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
  }
}
