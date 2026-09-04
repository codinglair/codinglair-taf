package com.codinglair.taf.messaging.jms;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.messaging.jms")
public class JmsProperties extends JmsControllerSettings {
  private boolean enabled;
  private final Map<String, JmsControllerSettings> controllers = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Map<String, JmsControllerSettings> getControllers() {
    return controllers;
  }

  public JmsControllerSettings settings(String name) {
    return controllers.getOrDefault(name, this);
  }
}
