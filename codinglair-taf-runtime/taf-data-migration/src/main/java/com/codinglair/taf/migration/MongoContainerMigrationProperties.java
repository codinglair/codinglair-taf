package com.codinglair.taf.migration;

import com.codinglair.taf.runtime.core.migration.MigrationPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Hardened one-shot Flyway configuration for a framework-owned context MongoDB. */
@ConfigurationProperties("taf.migration.mongodb")
public class MongoContainerMigrationProperties {
  public static final String APPROVED_IMAGE =
      "flyway/flyway@sha256:263d343d6a3ae9122fd12e45e3b68e29652c109425f45f4263e0b0babec31a5f";

  private boolean enabled;
  private String image = "";
  private String targetIdentity = "";
  private String history = "flyway_schema_history";
  private String networkId = "";
  private String uri = "";
  private MigrationPolicy policy = MigrationPolicy.DISABLED;
  private List<String> locations = new ArrayList<>();
  private final Map<String, History> histories = new LinkedHashMap<>();
  private Duration acquisitionTimeout = Duration.ofSeconds(30);
  private Duration executionTimeout = Duration.ofMinutes(2);
  private int maximumOutputBytes = 1_048_576;
  private long memoryBytes = 536_870_912;
  private long cpuCount = 1;
  private long pidLimit = 128;
  private long tmpfsBytes = 16_777_216;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = text(image);
  }

  public String getTargetIdentity() {
    return targetIdentity;
  }

  public void setTargetIdentity(String targetIdentity) {
    this.targetIdentity = text(targetIdentity);
  }

  public String getHistory() {
    return history;
  }

  public void setHistory(String history) {
    this.history = identifier(history, "history");
  }

  public String getNetworkId() {
    return networkId;
  }

  public void setNetworkId(String networkId) {
    this.networkId = text(networkId);
  }

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = text(uri);
  }

  public MigrationPolicy getPolicy() {
    return policy;
  }

  public void setPolicy(MigrationPolicy policy) {
    this.policy = java.util.Objects.requireNonNull(policy, "policy");
  }

  public List<String> getLocations() {
    return locations;
  }

  public void setLocations(List<String> locations) {
    this.locations = new ArrayList<>(java.util.Objects.requireNonNull(locations, "locations"));
  }

  public Map<String, History> getHistories() {
    return histories;
  }

  public Duration getAcquisitionTimeout() {
    return acquisitionTimeout;
  }

  public void setAcquisitionTimeout(Duration value) {
    acquisitionTimeout = positive(value, "acquisitionTimeout");
  }

  public Duration getExecutionTimeout() {
    return executionTimeout;
  }

  public void setExecutionTimeout(Duration value) {
    executionTimeout = positive(value, "executionTimeout");
  }

  public int getMaximumOutputBytes() {
    return maximumOutputBytes;
  }

  public void setMaximumOutputBytes(int value) {
    maximumOutputBytes = positive(value, "maximumOutputBytes");
  }

  public long getMemoryBytes() {
    return memoryBytes;
  }

  public void setMemoryBytes(long value) {
    memoryBytes = positive(value, "memoryBytes");
  }

  public long getCpuCount() {
    return cpuCount;
  }

  public void setCpuCount(long value) {
    cpuCount = positive(value, "cpuCount");
  }

  public long getPidLimit() {
    return pidLimit;
  }

  public void setPidLimit(long value) {
    pidLimit = positive(value, "pidLimit");
  }

  public long getTmpfsBytes() {
    return tmpfsBytes;
  }

  public void setTmpfsBytes(long value) {
    tmpfsBytes = positive(value, "tmpfsBytes");
  }

  void validateForExecution() {
    if (!APPROVED_IMAGE.equals(image)) {
      throw new IllegalArgumentException(
          "Mongo migration image must be the approved immutable platform digest");
    }
    if (targetIdentity.isBlank() || networkId.isBlank() || uri.isBlank()) {
      throw new IllegalArgumentException(
          "Mongo migration target identity, network and protected URI are required");
    }
    if (!uri.startsWith("mongodb://") || uri.contains("tls=true") || uri.contains("ssl=true")) {
      throw new IllegalArgumentException(
          "Only isolated non-TLS native MongoDB context connections are approved");
    }
  }

  History history(String name) {
    if (histories.isEmpty()) return new History(history, locations);
    History result = histories.get(name);
    if (result == null)
      throw new IllegalArgumentException("Mongo migration history is not configured");
    return result.validated();
  }

  public static class History {
    private String table = "";
    private List<String> locations = new ArrayList<>();

    public History() {}

    private History(String table, List<String> locations) {
      this.table = table;
      this.locations = List.copyOf(locations);
    }

    public String getTable() {
      return table;
    }

    public void setTable(String table) {
      this.table = identifier(table, "history table");
    }

    public List<String> getLocations() {
      return locations;
    }

    public void setLocations(List<String> locations) {
      this.locations = new ArrayList<>(java.util.Objects.requireNonNull(locations, "locations"));
    }

    History validated() {
      if (table.isBlank() || locations.isEmpty())
        throw new IllegalArgumentException("Mongo history table and locations are required");
      return this;
    }
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }

  private static String identifier(String value, String name) {
    String result = text(value);
    if (!result.matches("[A-Za-z0-9][A-Za-z0-9_]{0,62}")) {
      throw new IllegalArgumentException(name + " is invalid");
    }
    return result;
  }

  private static Duration positive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative())
      throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  private static int positive(int value, String name) {
    if (value < 1) throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  private static long positive(long value, String name) {
    if (value < 1) throw new IllegalArgumentException(name + " must be positive");
    return value;
  }
}
