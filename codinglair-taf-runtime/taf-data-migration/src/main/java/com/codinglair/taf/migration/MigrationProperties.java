package com.codinglair.taf.migration;

import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("taf.migration")
public class MigrationProperties {
  private boolean enabled;
  private final Map<String, Connection> connections = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Map<String, Connection> getConnections() {
    return connections;
  }

  public static class Connection {
    private MigrationPolicy policy = MigrationPolicy.DISABLED;
    private List<String> locations = new ArrayList<>();

    public MigrationPolicy getPolicy() {
      return policy;
    }

    public void setPolicy(MigrationPolicy value) {
      policy = value;
    }

    public List<String> getLocations() {
      return locations;
    }

    public void setLocations(List<String> value) {
      locations = value;
    }
  }
}
