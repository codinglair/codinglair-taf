package com.codinglair.taf.messaging.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.messaging.kafka")
public class KafkaProperties extends KafkaControllerSettings {
  private boolean enabled;
  private final Map<String, KafkaControllerSettings> controllers = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Map<String, KafkaControllerSettings> getControllers() {
    return controllers;
  }

  public KafkaControllerSettings settings(String name) {
    return controllers.getOrDefault(name, this);
  }
}
