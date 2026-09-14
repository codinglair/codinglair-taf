package com.codinglair.taf.messaging.aws.environment;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Immutable, secret-free configuration published by an AWS environment provider. */
public record AwsEnvironmentConfiguration(
    String profileName, URI endpoint, String region, List<AwsResourceDescriptor> resources) {
  public AwsEnvironmentConfiguration {
    Objects.requireNonNull(profileName, "profileName");
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(region, "region");
    resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
  }
}
