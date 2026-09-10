package com.codinglair.taf.messaging.aws.common;

import java.time.Duration;

public class AwsOperationPolicy {
  private Duration operationTimeout = Duration.ofSeconds(30);
  private Duration pollInterval = Duration.ofMillis(250);
  private int retryAttempts = 3;
  private Duration maximumVisibility = Duration.ofMinutes(15);
  private int maximumEvidenceBytes = 16_384;

  public Duration getOperationTimeout() {
    return operationTimeout;
  }

  public void setOperationTimeout(Duration value) {
    operationTimeout = value;
  }

  public Duration getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(Duration value) {
    pollInterval = value;
  }

  public int getRetryAttempts() {
    return retryAttempts;
  }

  public void setRetryAttempts(int value) {
    retryAttempts = value;
  }

  public Duration getMaximumVisibility() {
    return maximumVisibility;
  }

  public void setMaximumVisibility(Duration value) {
    maximumVisibility = value;
  }

  public int getMaximumEvidenceBytes() {
    return maximumEvidenceBytes;
  }

  public void setMaximumEvidenceBytes(int value) {
    maximumEvidenceBytes = value;
  }

  void validate(String path) {
    require(
        operationTimeout,
        Duration.ofMillis(100),
        Duration.ofMinutes(5),
        path + ".operation-timeout");
    require(pollInterval, Duration.ofMillis(10), operationTimeout, path + ".poll-interval");
    if (retryAttempts < 0 || retryAttempts > 10)
      fail(path + ".retry-attempts", "must be between 0 and 10");
    require(maximumVisibility, Duration.ZERO, Duration.ofHours(12), path + ".maximum-visibility");
    if (maximumEvidenceBytes < 0 || maximumEvidenceBytes > 1_048_576)
      fail(path + ".maximum-evidence-bytes", "must be between 0 and 1048576");
  }

  private static void require(Duration value, Duration minimum, Duration maximum, String path) {
    if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0)
      fail(path, "is outside the supported bound " + minimum + ".." + maximum);
  }

  public static void fail(String path, String reason) {
    throw new AwsConfigurationException(path, reason);
  }
}
