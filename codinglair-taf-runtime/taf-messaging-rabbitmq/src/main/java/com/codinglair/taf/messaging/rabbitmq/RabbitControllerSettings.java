package com.codinglair.taf.messaging.rabbitmq;

import java.time.Duration;

/** Strongly typed settings for one named RabbitMQ controller. */
public class RabbitControllerSettings {
  private String addresses = "localhost:5672";
  private String username = "guest";
  private String passwordReference = "credential://profile/rabbitmq";
  private String virtualHost = "/";
  private Duration operationTimeout = Duration.ofSeconds(10);
  private int maximumBufferedRecords = 1_000;

  public String getAddresses() {
    return addresses;
  }

  public void setAddresses(String value) {
    addresses = value;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String value) {
    username = value;
  }

  public String getPasswordReference() {
    return passwordReference;
  }

  public void setPasswordReference(String value) {
    passwordReference = value;
  }

  public String getVirtualHost() {
    return virtualHost;
  }

  public void setVirtualHost(String value) {
    virtualHost = value;
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
    requireText(addresses, prefix + ".addresses");
    requireText(username, prefix + ".username");
    requireText(passwordReference, prefix + ".password-reference");
    requireText(virtualHost, prefix + ".virtual-host");
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
