package com.codinglair.taf.api.rest;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.api.rest")
public class RestProperties extends RestControllerSettings {
  private boolean enabled;
  private final Map<String, RestControllerSettings> controllers = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Map<String, RestControllerSettings> getControllers() {
    return controllers;
  }

  public RestControllerSettings settings(String name) {
    return controllers.getOrDefault(name, this);
  }
}
