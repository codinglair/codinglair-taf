package com.codinglair.taf.mobile.appium.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("taf.mobile.apple")
public class AppleProperties extends AppleControllerSettings {
  private boolean enabled;
  private final Map<String, AppleControllerSettings> controllers = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Map<String, AppleControllerSettings> getControllers() {
    return controllers;
  }

  public AppleControllerSettings settings(String name) {
    if (!controllers.isEmpty() && !controllers.containsKey(name))
      throw new IllegalArgumentException("Unknown Apple controller name");
    return resolve(this, controllers.get(name), null);
  }
}
