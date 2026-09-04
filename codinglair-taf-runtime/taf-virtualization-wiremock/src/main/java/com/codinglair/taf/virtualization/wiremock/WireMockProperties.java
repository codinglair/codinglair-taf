package com.codinglair.taf.virtualization.wiremock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.virtualization.wiremock")
public class WireMockProperties {
  private boolean enabled;
  private boolean containerEnabled;
  private boolean networkFaultsEnabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public boolean isContainerEnabled() {
    return containerEnabled;
  }

  public void setContainerEnabled(boolean value) {
    containerEnabled = value;
  }

  public boolean isNetworkFaultsEnabled() {
    return networkFaultsEnabled;
  }

  public void setNetworkFaultsEnabled(boolean value) {
    networkFaultsEnabled = value;
  }
}
