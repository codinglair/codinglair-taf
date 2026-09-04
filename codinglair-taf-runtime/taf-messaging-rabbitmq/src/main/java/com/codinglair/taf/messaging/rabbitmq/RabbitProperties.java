package com.codinglair.taf.messaging.rabbitmq;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.messaging.rabbitmq")
public class RabbitProperties extends RabbitControllerSettings {
  private boolean enabled;
  private final Map<String, RabbitControllerSettings> controllers = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Map<String, RabbitControllerSettings> getControllers() {
    return controllers;
  }

  public RabbitControllerSettings settings(String name) {
    return controllers.getOrDefault(name, this);
  }
}
