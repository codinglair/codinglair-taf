package com.codinglair.taf.runtime.core.security;

import java.util.Set;

/** Default-deny resource policy; independent of secret retrieval, MCP and identity providers. */
@FunctionalInterface
public interface ResourceAuthorizer {
  boolean permits(ResourceAccess access);

  default void require(ResourceAccess access) {
    if (!permits(access)) throw new SecurityException("Resource access denied");
  }

  static ResourceAuthorizer trusted(Set<ResourceAccess> grants) {
    var snapshot = Set.copyOf(grants);
    return request -> snapshot.stream().anyMatch(grant -> grant.covers(request));
  }

  default ResourceAuthorizer intersect(ResourceAuthorizer other) {
    return request -> permits(request) && other.permits(request);
  }
}
