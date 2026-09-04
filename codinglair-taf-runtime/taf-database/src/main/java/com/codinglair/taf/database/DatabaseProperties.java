package com.codinglair.taf.database;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("taf.database")
@Validated
public class DatabaseProperties {
  private boolean enabled;
  private String environment = "local";
  private final Map<String, Connection> connections = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String value) {
    environment = value;
  }

  public Map<String, Connection> getConnections() {
    return connections;
  }

  public static class Connection {
    private String technology = "postgresql";
    private String jdbcUrl;
    private String username = "";
    private String passwordReference = "";
    private ConnectionMode mode = ConnectionMode.EXTERNAL;
    private DatabaseAccess access = DatabaseAccess.READ_ONLY;
    private Duration timeout = Duration.ofSeconds(10);
    private CleanupPolicy cleanupPolicy = CleanupPolicy.NONE;
    private boolean setupAuthorized;
    private boolean cleanupAuthorized;
    private Set<String> sensitiveColumns = new LinkedHashSet<>();
    private String cleanupSql = "";

    public String getTechnology() {
      return technology;
    }

    public void setTechnology(String v) {
      technology = v;
    }

    public String getJdbcUrl() {
      return jdbcUrl;
    }

    public void setJdbcUrl(String v) {
      jdbcUrl = v;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String v) {
      username = v;
    }

    public String getPasswordReference() {
      return passwordReference;
    }

    public void setPasswordReference(String v) {
      passwordReference = v;
    }

    public ConnectionMode getMode() {
      return mode;
    }

    public void setMode(ConnectionMode v) {
      mode = v;
    }

    public DatabaseAccess getAccess() {
      return access;
    }

    public void setAccess(DatabaseAccess v) {
      access = v;
    }

    public Duration getTimeout() {
      return timeout;
    }

    public void setTimeout(Duration v) {
      timeout = v;
    }

    public CleanupPolicy getCleanupPolicy() {
      return cleanupPolicy;
    }

    public void setCleanupPolicy(CleanupPolicy v) {
      cleanupPolicy = v;
    }

    public boolean isSetupAuthorized() {
      return setupAuthorized;
    }

    public void setSetupAuthorized(boolean v) {
      setupAuthorized = v;
    }

    public boolean isCleanupAuthorized() {
      return cleanupAuthorized;
    }

    public void setCleanupAuthorized(boolean v) {
      cleanupAuthorized = v;
    }

    public Set<String> getSensitiveColumns() {
      return sensitiveColumns;
    }

    public void setSensitiveColumns(Set<String> v) {
      sensitiveColumns = v;
    }

    public String getCleanupSql() {
      return cleanupSql;
    }

    public void setCleanupSql(String v) {
      cleanupSql = v;
    }

    SutConnectionDescriptor descriptor(String name) {
      return new SutConnectionDescriptor(
          name,
          technology,
          jdbcUrl,
          username,
          passwordReference,
          mode,
          access,
          timeout,
          cleanupPolicy,
          setupAuthorized,
          cleanupAuthorized,
          sensitiveColumns,
          cleanupSql);
    }
  }
}
