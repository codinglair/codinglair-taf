package com.codinglair.taf.mobile.appium.service;

import com.codinglair.taf.mobile.MobileDeviceFamily;
import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.exception.AppleControllerException;
import com.codinglair.taf.mobile.appium.platform.ApplePlatformStrategy;
import com.codinglair.taf.runtime.core.controller.ArtifactReason;
import com.codinglair.taf.runtime.core.controller.ControllerContext;
import com.codinglair.taf.runtime.core.controller.ControllerIdentity;
import com.codinglair.taf.runtime.core.controller.ControllerState;
import com.codinglair.taf.runtime.core.controller.HealthResult;
import com.codinglair.taf.runtime.core.reporting.abstraction.TestArtifact;
import io.appium.java_client.ios.IOSDriver;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** One lazy driver per controller. Serialized lifecycle prevents close/initialize races. */
public final class DefaultAppleController implements AppleController {
  private final ControllerIdentity identity;
  private final AppleControllerSettings settings;
  private ControllerState state = ControllerState.NEW;
  private IOSDriver driver;

  public DefaultAppleController(
      String name, AppleControllerSettings base, AppleControllerSettings instance) {
    identity = new ControllerIdentity(AppleController.class, name);
    // Passive construction: validation and network access occur only at initialization.
    settings = snapshot(base, instance);
  }

  private static AppleControllerSettings snapshot(
      AppleControllerSettings base, AppleControllerSettings instance) {
    return AppleControllerSettings.merge(base, instance, null);
  }

  @Override
  public ControllerIdentity identity() {
    return identity;
  }

  @Override
  public synchronized ControllerState state() {
    return state;
  }

  @Override
  public MobileDeviceFamily family() {
    return settings.family();
  }

  @Override
  public synchronized void initialize(ControllerContext context) {
    Objects.requireNonNull(context);
    if (state != ControllerState.NEW)
      throw new IllegalStateException("Apple controller cannot initialize from " + state);
    state = ControllerState.INITIALIZING;
    try {
      driver = new ApplePlatformStrategy().create(settings);
      state = ControllerState.READY;
    } catch (RuntimeException failure) {
      state = ControllerState.FAILED;
      throw new AppleControllerException("initialize");
    }
  }

  @Override
  public synchronized IOSDriver nativeDriver() {
    if (state != ControllerState.READY)
      throw new IllegalStateException("Apple controller is not ready");
    return driver;
  }

  @Override
  public synchronized HealthResult health() {
    return switch (state) {
      case READY ->
          new HealthResult(
              HealthResult.Status.HEALTHY,
              "Apple session is ready",
              Map.of("family", family().name()));
      case FAILED, CLOSED ->
          new HealthResult(
              HealthResult.Status.UNAVAILABLE, "Apple session state is " + state, Map.of());
      default -> HealthResult.unknown("Apple session state is " + state);
    };
  }

  @Override
  public Stream<TestArtifact> collectArtifacts(ArtifactReason reason) {
    return Stream.empty(); // Conditional Apple evidence is owned by MOB-130-005.
  }

  @Override
  public synchronized void close() {
    if (state == ControllerState.CLOSED) return;
    state = ControllerState.CLOSED;
    IOSDriver owned = driver;
    driver = null;
    if (owned != null) {
      try {
        owned.quit();
      } catch (RuntimeException failure) {
        throw new AppleControllerException("close");
      }
    }
  }
}
