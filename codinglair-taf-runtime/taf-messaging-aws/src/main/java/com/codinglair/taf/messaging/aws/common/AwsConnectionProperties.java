package com.codinglair.taf.messaging.aws.common;

import com.codinglair.taf.messaging.aws.eventbridge.EventBridgeControllerProperties;
import com.codinglair.taf.messaging.aws.sqs.SqsControllerProperties;
import com.codinglair.taf.runtime.secret.SecretReference;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public class AwsConnectionProperties {
  private AwsEndpointMode endpointMode = AwsEndpointMode.AWS;
  private URI endpointOverride;
  private String region;
  private String credentialProfileReference;
  private AwsOwnershipMode ownershipMode = AwsOwnershipMode.EXTERNAL;
  private AwsOperationPolicy policy = new AwsOperationPolicy();
  private final Map<String, String> resourceAliases = new LinkedHashMap<>();
  private final Map<String, SqsControllerProperties> sqs = new LinkedHashMap<>();
  private final Map<String, EventBridgeControllerProperties> eventbridge = new LinkedHashMap<>();

  public AwsEndpointMode getEndpointMode() {
    return endpointMode;
  }

  public void setEndpointMode(AwsEndpointMode value) {
    endpointMode = value;
  }

  public URI getEndpointOverride() {
    return endpointOverride;
  }

  public void setEndpointOverride(URI value) {
    endpointOverride = value;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String value) {
    region = value;
  }

  public String getCredentialProfileReference() {
    return credentialProfileReference;
  }

  public void setCredentialProfileReference(String value) {
    credentialProfileReference = value;
  }

  public AwsOwnershipMode getOwnershipMode() {
    return ownershipMode;
  }

  public void setOwnershipMode(AwsOwnershipMode value) {
    ownershipMode = value;
  }

  public AwsOperationPolicy getPolicy() {
    return policy;
  }

  public void setPolicy(AwsOperationPolicy value) {
    policy = value;
  }

  public Map<String, String> getResourceAliases() {
    return resourceAliases;
  }

  public Map<String, SqsControllerProperties> getSqs() {
    return sqs;
  }

  public Map<String, EventBridgeControllerProperties> getEventbridge() {
    return eventbridge;
  }

  public void validate(String path) {
    String resolvedPath = path == null || path.isBlank() ? "taf.aws.profiles.<profile>" : path;
    if (region == null || region.isBlank())
      AwsOperationPolicy.fail(resolvedPath + ".region", "is required");
    if (endpointMode == null)
      AwsOperationPolicy.fail(resolvedPath + ".endpoint-mode", "is required");
    if (endpointMode == AwsEndpointMode.AWS && ownershipMode == AwsOwnershipMode.TEST_OWNED)
      AwsOperationPolicy.fail(
          resolvedPath + ".ownership-mode",
          "test-owned provisioning is supported only by LocalStack");
    if (endpointOverride != null
        && !java.util.Set.of("http", "https").contains(endpointOverride.getScheme()))
      AwsOperationPolicy.fail(resolvedPath + ".endpoint-override", "must use http or https");
    if (endpointOverride != null
        && (endpointOverride.getHost() == null
            || endpointOverride.getUserInfo() != null
            || endpointOverride.getQuery() != null
            || endpointOverride.getFragment() != null))
      AwsOperationPolicy.fail(
          resolvedPath + ".endpoint-override",
          "must contain a host and must not contain user-info, query, or fragment components");
    if (credentialProfileReference != null) {
      SecretReference reference = SecretReference.parse(credentialProfileReference);
      if (!reference.scheme().equals("credential"))
        AwsOperationPolicy.fail(
            resolvedPath + ".credential-profile-reference",
            "must use a credential:// profile reference");
    }
    if (ownershipMode == null)
      AwsOperationPolicy.fail(resolvedPath + ".ownership-mode", "is required");
    if (policy == null) AwsOperationPolicy.fail(resolvedPath + ".policy", "is required");
    policy.validate(resolvedPath + ".policy");
    sqs.forEach((name, value) -> value.validate(resolvedPath + ".sqs." + name, ownershipMode));
    eventbridge.forEach((name, value) -> value.validate(resolvedPath + ".eventbridge." + name));
  }
}
