package com.codinglair.taf.runtime.environment.spring;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed, named capability configuration shared by scaffolded Spring consumers. */
@ConfigurationProperties("taf.consumer")
public class ConsumerConfigurationProperties {
  public static final String UNRESOLVED_VALUE = "REPLACE_ME";

  private String environment = "default";
  private final Map<String, CapabilityInstance> capabilities = new LinkedHashMap<>();

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public Map<String, CapabilityInstance> getCapabilities() {
    return capabilities;
  }

  /** One logical, named controller/capability instance. */
  public static class CapabilityInstance {
    private boolean enabled = true;
    private String type = "custom";
    private final Map<String, String> requiredValues = new LinkedHashMap<>();
    private final Map<String, String> secretReferences = new LinkedHashMap<>();
    private final Set<String> requires = new LinkedHashSet<>();
    private final Set<String> incompatibleWith = new LinkedHashSet<>();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Map<String, String> getRequiredValues() {
      return requiredValues;
    }

    public Map<String, String> getSecretReferences() {
      return secretReferences;
    }

    public Set<String> getRequires() {
      return requires;
    }

    public Set<String> getIncompatibleWith() {
      return incompatibleWith;
    }
  }
}
