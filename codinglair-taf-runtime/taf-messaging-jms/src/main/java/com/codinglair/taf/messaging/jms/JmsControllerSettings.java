package com.codinglair.taf.messaging.jms;

/** Settings independent of a concrete JMS provider. */
public class JmsControllerSettings {
  private String clientId;
  private int maximumEvidenceRecords = 1000;

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String value) {
    clientId = value;
  }

  public int getMaximumEvidenceRecords() {
    return maximumEvidenceRecords;
  }

  public void setMaximumEvidenceRecords(int value) {
    maximumEvidenceRecords = value;
  }

  void validate(String prefix) {
    if (maximumEvidenceRecords < 1)
      throw new IllegalArgumentException(prefix + ".maximum-evidence-records must be positive");
  }
}
