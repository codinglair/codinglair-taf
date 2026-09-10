package com.codinglair.taf.messaging.aws.environment;

import java.util.List;

/** Extension boundary for service-specific provisioning without coupling controllers to it. */
public interface AwsServiceEnvironmentContributor {
  String service();

  List<AwsResourceDescriptor> describeResources(String profileName);
}
