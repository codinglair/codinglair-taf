package com.codinglair.taf.mobile.appium;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.mobile.android")
public class AndroidProperties extends AndroidControllerSettings {
  private boolean enabled;
  private final Map<String, AndroidControllerSettings> controllers = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean v) {
    enabled = v;
  }

  public Map<String, AndroidControllerSettings> getControllers() {
    return controllers;
  }

  AndroidControllerSettings settings(String name) {
    return controllers.getOrDefault(name, this);
  }
}
