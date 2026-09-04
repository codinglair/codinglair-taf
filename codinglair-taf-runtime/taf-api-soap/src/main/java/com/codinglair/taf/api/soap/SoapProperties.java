package com.codinglair.taf.api.soap;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.api.soap")
/**
 * SOAP capability properties. The capability is disabled by default, timeout defaults to 30
 * seconds, maximum response size defaults to 10 MiB, and no endpoint is assumed.
 */
public class SoapProperties extends SoapControllerSettings {
  private boolean enabled;
  private final Map<String, SoapControllerSettings> controllers = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Map<String, SoapControllerSettings> getControllers() {
    return controllers;
  }

  public SoapControllerSettings settings(String name) {
    return controllers.getOrDefault(name, this);
  }
}
