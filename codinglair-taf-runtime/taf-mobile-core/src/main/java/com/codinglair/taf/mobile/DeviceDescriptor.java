package com.codinglair.taf.mobile;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** A provisioned device and Appium endpoint. Provisioning remains provider-owned. */
public record DeviceDescriptor(
    String id,
    MobilePlatform platform,
    DeviceMode mode,
    URI serverUri,
    Map<String, Object> capabilities) {
  public DeviceDescriptor {
    if (id == null || id.isBlank())
      throw new IllegalArgumentException("Device id must not be blank");
    Objects.requireNonNull(platform, "platform");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(serverUri, "serverUri");
    capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
  }
}
