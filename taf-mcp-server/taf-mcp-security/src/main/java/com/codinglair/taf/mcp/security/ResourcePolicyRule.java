package com.codinglair.taf.mcp.security;

import com.codinglair.taf.runtime.core.security.ResourceAccess;
import java.util.Objects;
import java.util.Set;

/** Resource selectors supplement the existing caller/action selectors; never replace them. */
public record ResourcePolicyRule(PolicyRule scope, Set<ResourceAccess> resources) {
  public ResourcePolicyRule {
    Objects.requireNonNull(scope);
    resources = Set.copyOf(resources);
    if (resources.isEmpty()) throw new IllegalArgumentException("Resource selectors are required");
  }

  boolean appliesTo(AuthorizationContext context, ResourceAccess resource) {
    return scope.appliesTo(context) && resources.stream().anyMatch(grant -> grant.covers(resource));
  }
}
