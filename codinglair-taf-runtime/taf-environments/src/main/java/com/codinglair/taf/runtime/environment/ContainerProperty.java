package com.codinglair.taf.runtime.environment;

import org.testcontainers.containers.GenericContainer;

/** Resolves one non-secret dynamic property after a container has started. */
@FunctionalInterface
public interface ContainerProperty {
  String resolve(GenericContainer<?> container);

  static ContainerProperty host() {
    return GenericContainer::getHost;
  }

  static ContainerProperty mappedPort(int containerPort) {
    if (containerPort < 1 || containerPort > 65_535) {
      throw new IllegalArgumentException("Container port must be between 1 and 65535");
    }
    return container -> Integer.toString(container.getMappedPort(containerPort));
  }

  static ContainerProperty endpoint(String scheme, int containerPort) {
    if (scheme == null || scheme.isBlank()) {
      throw new IllegalArgumentException("Endpoint scheme must not be blank");
    }
    ContainerProperty port = mappedPort(containerPort);
    return container -> scheme + "://" + container.getHost() + ":" + port.resolve(container);
  }
}
