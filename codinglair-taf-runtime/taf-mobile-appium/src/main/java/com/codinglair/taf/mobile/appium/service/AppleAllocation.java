package com.codinglair.taf.mobile.appium.service;

import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import com.codinglair.taf.mobile.appium.platform.ApplePlatformStrategy;
import com.codinglair.taf.runtime.core.controller.EnvironmentAccess;

/** Consumes a preallocated resource. Its provider/TestSession owns allocation release. */
final class AppleAllocation {
  private AppleAllocation() {}

  static String apply(AppleControllerSettings settings, EnvironmentAccess environments) {
    if (settings.getAllocationResource() == null) return null;
    var resource = environments.resource(settings.getAllocationResource());
    if (!"apple-session-allocation".equals(resource.type()))
      throw new IllegalArgumentException("Expected an authorized Apple session allocation");
    var values = resource.properties();
    if (!"true".equals(values.get("exclusive")))
      throw new IllegalArgumentException("Apple allocation requires exclusive provider ownership");
    String target = values.get("device-id");
    if (target == null || target.isBlank())
      throw new IllegalArgumentException("Apple allocation requires a unique target identifier");
    settings.setDeviceId(target);
    var wda = settings.getWda();
    if (values.containsKey("wda-port")) wda.setLocalPort(Integer.valueOf(values.get("wda-port")));
    if (values.containsKey("mjpeg-port"))
      wda.setMjpegPort(Integer.valueOf(values.get("mjpeg-port")));
    if (values.containsKey("derived-data-path"))
      wda.setDerivedDataPath(values.get("derived-data-path"));
    String session = values.get("owned-session-id");
    if (session != null && !session.matches("[A-Za-z0-9_-]{1,128}"))
      throw new IllegalArgumentException("Provider owned session identifier is invalid");
    return session;
  }

  /** Only a provider-identified owned session, never a session enumeration or global deletion. */
  static void cleanup(AppleControllerSettings settings, String session) throws Exception {
    cleanup(settings, session, AppleTransportSecurity.trusted(settings, null), "standalone");
  }

  static void cleanup(
      AppleControllerSettings settings,
      String session,
      AppleTransportSecurity security,
      String sessionId)
      throws Exception {
    new ApplePlatformStrategy().cleanupOwned(settings, session, security, sessionId);
  }
}
