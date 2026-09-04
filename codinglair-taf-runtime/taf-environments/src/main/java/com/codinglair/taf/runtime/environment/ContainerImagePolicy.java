package com.codinglair.taf.runtime.environment;

import org.testcontainers.utility.DockerImageName;

/** Approval boundary for container images. */
@FunctionalInterface
public interface ContainerImagePolicy {
  boolean isAllowed(DockerImageName image);

  static ContainerImagePolicy allow(DockerImageName approvedImage) {
    return candidate -> approvedImage.equals(candidate);
  }
}
