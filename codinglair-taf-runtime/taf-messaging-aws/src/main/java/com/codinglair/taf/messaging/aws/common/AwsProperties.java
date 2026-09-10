package com.codinglair.taf.messaging.aws.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("taf.aws")
public class AwsProperties {
  private boolean enabled;
  private String localstackImage = "localstack/localstack:4.8.1";
  private final Map<String, AwsConnectionProperties> profiles = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Map<String, AwsConnectionProperties> getProfiles() {
    return profiles;
  }

  public String getLocalstackImage() {
    return localstackImage;
  }

  public void setLocalstackImage(String value) {
    localstackImage = value;
  }

  public void validate() {
    if (localstackImage == null || localstackImage.isBlank())
      AwsOperationPolicy.fail("taf.aws.localstack-image", "is required");
    if (profiles.isEmpty())
      AwsOperationPolicy.fail("taf.aws.profiles", "at least one named profile is required");
    profiles.forEach((name, value) -> value.validate("taf.aws.profiles." + name));
  }
}
