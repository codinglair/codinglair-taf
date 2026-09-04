package com.codinglair.taf.messaging.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Strongly typed settings for one named Kafka controller. */
public class KafkaControllerSettings {
  private List<String> bootstrapServers = new ArrayList<>();
  private String groupId = "taf";
  private String clientIdPrefix = "taf";
  private KafkaTopicPolicy topicPolicy = KafkaTopicPolicy.REQUIRE_EXISTING;
  private int topicPartitions = 1;
  private short topicReplicationFactor = 1;
  private Duration operationTimeout = Duration.ofSeconds(10);
  private int maximumBufferedRecords = 1_000;

  public List<String> getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(List<String> value) {
    bootstrapServers = value;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String value) {
    groupId = value;
  }

  public String getClientIdPrefix() {
    return clientIdPrefix;
  }

  public void setClientIdPrefix(String value) {
    clientIdPrefix = value;
  }

  public KafkaTopicPolicy getTopicPolicy() {
    return topicPolicy;
  }

  public void setTopicPolicy(KafkaTopicPolicy value) {
    topicPolicy = value;
  }

  public int getTopicPartitions() {
    return topicPartitions;
  }

  public void setTopicPartitions(int value) {
    topicPartitions = value;
  }

  public short getTopicReplicationFactor() {
    return topicReplicationFactor;
  }

  public void setTopicReplicationFactor(short value) {
    topicReplicationFactor = value;
  }

  public Duration getOperationTimeout() {
    return operationTimeout;
  }

  public void setOperationTimeout(Duration value) {
    operationTimeout = value;
  }

  public int getMaximumBufferedRecords() {
    return maximumBufferedRecords;
  }

  public void setMaximumBufferedRecords(int value) {
    maximumBufferedRecords = value;
  }

  void validate(String prefix) {
    if (bootstrapServers == null
        || bootstrapServers.isEmpty()
        || bootstrapServers.stream().anyMatch(value -> value == null || value.isBlank()))
      throw new IllegalArgumentException(prefix + ".bootstrap-servers must contain an endpoint");
    requireText(groupId, prefix + ".group-id");
    requireText(clientIdPrefix, prefix + ".client-id-prefix");
    if (topicPolicy == null)
      throw new IllegalArgumentException(prefix + ".topic-policy is required");
    if (topicPartitions < 1)
      throw new IllegalArgumentException(prefix + ".topic-partitions must be positive");
    if (topicReplicationFactor < 1)
      throw new IllegalArgumentException(prefix + ".topic-replication-factor must be positive");
    if (operationTimeout == null || operationTimeout.isZero() || operationTimeout.isNegative())
      throw new IllegalArgumentException(prefix + ".operation-timeout must be positive");
    if (maximumBufferedRecords < 1)
      throw new IllegalArgumentException(prefix + ".maximum-buffered-records must be positive");
  }

  private static void requireText(String value, String property) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(property + " must not be blank");
  }
}
