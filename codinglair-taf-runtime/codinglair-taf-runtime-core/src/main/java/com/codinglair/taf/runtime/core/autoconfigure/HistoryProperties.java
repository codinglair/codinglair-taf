package com.codinglair.taf.runtime.core.autoconfigure;

import com.codinglair.taf.runtime.core.history.HistoryConfiguration;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed RT-012 history and signature bounds. */
@ConfigurationProperties("taf.history")
public class HistoryProperties {
  private boolean enabled;
  private Path root = Path.of("target", "taf-evidence", "history");
  private int maximumRecords = 500;
  private Duration maximumAge = Duration.ofDays(30);
  private long maximumBytes = 50L * 1024 * 1024;
  private int signatureMessageLimit = 2048;
  private int signatureStackFrameLimit = 12;
  private int signatureCauseLimit = 4;
  private int minimumSamples = 2;
  private String projectId = "default";
  private String buildId = "unknown";
  private String environmentId = "local";
  private HistoryConfiguration.UnavailabilityPolicy unavailabilityPolicy = HistoryConfiguration.UnavailabilityPolicy.CONTINUE;
  private HistoryConfiguration.CorruptionPolicy corruptionPolicy = HistoryConfiguration.CorruptionPolicy.QUARANTINE;

  public HistoryConfiguration configuration() {
    if (signatureMessageLimit < 1 || signatureStackFrameLimit < 0 || signatureCauseLimit < 1 || minimumSamples < 2) throw new IllegalArgumentException("invalid history/signature bounds");
    return new HistoryConfiguration(enabled, root, maximumRecords, maximumAge, maximumBytes,
        unavailabilityPolicy, corruptionPolicy);
  }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public Path getRoot() { return root; }
  public void setRoot(Path root) { this.root = root; }
  public int getMaximumRecords() { return maximumRecords; }
  public void setMaximumRecords(int value) { this.maximumRecords = value; }
  public Duration getMaximumAge() { return maximumAge; }
  public void setMaximumAge(Duration value) { this.maximumAge = value; }
  public long getMaximumBytes() { return maximumBytes; }
  public void setMaximumBytes(long value) { this.maximumBytes = value; }
  public int getSignatureMessageLimit() { return signatureMessageLimit; }
  public void setSignatureMessageLimit(int value) { this.signatureMessageLimit = value; }
  public int getSignatureStackFrameLimit() { return signatureStackFrameLimit; }
  public void setSignatureStackFrameLimit(int value) { this.signatureStackFrameLimit = value; }
  public int getSignatureCauseLimit() { return signatureCauseLimit; }
  public void setSignatureCauseLimit(int value) { this.signatureCauseLimit = value; }
  public int getMinimumSamples() { return minimumSamples; }
  public void setMinimumSamples(int value) { this.minimumSamples = value; }
  public String getProjectId() { return projectId; }
  public void setProjectId(String value) { this.projectId = value; }
  public String getBuildId() { return buildId; }
  public void setBuildId(String value) { this.buildId = value; }
  public String getEnvironmentId() { return environmentId; }
  public void setEnvironmentId(String value) { this.environmentId = value; }
  public HistoryConfiguration.UnavailabilityPolicy getUnavailabilityPolicy() { return unavailabilityPolicy; }
  public void setUnavailabilityPolicy(HistoryConfiguration.UnavailabilityPolicy value) { this.unavailabilityPolicy = value; }
  public HistoryConfiguration.CorruptionPolicy getCorruptionPolicy() { return corruptionPolicy; }
  public void setCorruptionPolicy(HistoryConfiguration.CorruptionPolicy value) { this.corruptionPolicy = value; }
}
