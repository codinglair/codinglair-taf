package com.codinglair.taf.mobile.appium.service;

import com.codinglair.taf.mobile.appium.configuration.AppleControllerSettings;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Observable collisions within one explicitly shared coordinator; not a distributed allocator. */
public final class AppleResourceReservations {
  private final Set<String> held = new HashSet<>();

  public synchronized AutoCloseable acquire(AppleControllerSettings settings) {
    var keys = new HashSet<String>();
    var endpoint = settings.getServerUrl();
    // Paths on the same server do not create separate device/port namespaces.
    String host = endpoint.getHost().toLowerCase(Locale.ROOT);
    if (settings.getDeviceId() != null) keys.add(host + ":target:" + settings.getDeviceId());
    var wda = settings.getWda();
    if (wda.getLocalPort() != null) keys.add(host + ":port:" + wda.getLocalPort());
    if (wda.getMjpegPort() != null) keys.add(host + ":port:" + wda.getMjpegPort());
    if (wda.getDerivedDataPath() != null) keys.add(host + ":data:" + wda.getDerivedDataPath());
    if (wda.getBaseUrl() != null) keys.add("wda:" + wda.getBaseUrl().normalize());
    if (keys.stream().anyMatch(held::contains))
      throw new IllegalStateException(
          "Apple target/WDA resource collision; obtain a distinct provider allocation");
    held.addAll(keys);
    var released = new AtomicBoolean();
    return () -> {
      synchronized (AppleResourceReservations.this) {
        if (released.compareAndSet(false, true)) held.removeAll(keys);
      }
    };
  }
}
